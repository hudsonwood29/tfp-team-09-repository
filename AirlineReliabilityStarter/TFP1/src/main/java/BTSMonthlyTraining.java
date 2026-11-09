import smile.classification.LogisticRegression;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class BTSMonthlyTraining {

    private static final int TRAINING_LIMIT = 50_000;
    private static final int FEATURE_COUNT = 128;

    public static void main(String[] args) throws IOException {
        Path dataFolder = findDataFolder(args);
        Path january = requireFile(dataFolder.resolve("T_ONTIME_REPORTING.csv"));
        Path february = requireFile(dataFolder.resolve("feb.csv"));
        Path march = requireFile(dataFolder.resolve("march.csv"));

        System.out.println("Using data folder: " + dataFolder.toAbsolutePath());

        var predictor = trainPredictor(january);

        testMonth("February 2026", february, predictor);
        testMonth("March 2026", march, predictor);

        System.out.println("\nLower Brier score is better.");

        // This checks the predictor without making an Ignav request.
        var example = predictor.predict(
                "AA", "DFW", "JFK",
                LocalDateTime.of(2026, 12, 14, 10, 0)
        );

        System.out.println("\nSample prediction: AA DFW -> JFK, Monday at 10:00 AM");
        System.out.printf("Estimated 15+ minute arrival delay risk: %.1f%%%n",
                100 * example.delayProbability());
        System.out.printf("Experimental cancellation estimate: %.1f%%%n",
                100 * example.experimentalCancellationProbability());
        System.out.printf("Overall historical January cancellation rate: %.1f%%%n",
                100 * example.historicalCancellationRate());
    }

    // The website can call this once at startup. It only needs the January CSV.
    public static FlightRiskPredictorIgnavandHistorical trainPredictor(
            Path januaryCsv
    ) throws IOException {
        TrainingSample delayTraining = new TrainingSample();
        TrainingSample cancellationTraining = new TrainingSample();

        readFlights(requireFile(januaryCsv), flight -> {
            cancellationTraining.add(flight, flight.cancelled());

            if (flight.hasDelayLabel()) {
                delayTraining.add(flight, flight.late());
            }
        });

        System.out.println("\nTraining on January 2026");

        return new FlightRiskPredictorIgnavandHistorical(
                delayTraining.fit("Late arrival"),
                cancellationTraining.fit("Cancellation"),
                cancellationTraining.eventRate()
        );
    }

    // AI helped me share this method so the predictor and the training code
    // turn airline, airport, hour, and weekday into numbers the same way.
    static double[] predictionFeatures(
            String operatingCarrier, String origin, String destination,
            int departureHour, int weekday
    ) {
        double[] features = new double[FEATURE_COUNT];

        features[Math.floorMod(("airline:" + operatingCarrier).hashCode(), 97)]++;
        features[Math.floorMod(("origin:" + origin).hashCode(), 97)]++;
        features[Math.floorMod(("dest:" + destination).hashCode(), 97)]++;
        features[97 + departureHour] = 1;
        features[120 + weekday] = 1;

        return features;
    }

    private static void testMonth(
            String month, Path file,
            FlightRiskPredictorIgnavandHistorical predictor
    ) throws IOException {
        // Keep the actual flight outcomes out of the predictor.
        // The date is only used to pass the correct weekday and local hour.
        LocalDateTime monday = LocalDateTime.of(2026, 1, 5, 0, 0);
        Score delay = new Score();
        Score cancellation = new Score();

        readFlights(file, flight -> {
            LocalDateTime departure = monday.plusDays(flight.weekday() - 1)
                    .withHour(flight.hour());

            var risk = predictor.predict(
                    flight.airline(), flight.origin(), flight.destination(),
                    departure
            );

            cancellation.add(
                    risk.experimentalCancellationProbability(),
                    flight.cancelled()
            );

            if (flight.hasDelayLabel()) {
                delay.add(risk.delayProbability(), flight.late());
            }
        });

        System.out.println("\nTesting on " + month + " (" + file.getFileName() + ")");
        delay.print("Late arrival (15+ minutes)");
        cancellation.print("Cancellation");
    }

    private static Path findDataFolder(String[] args) throws IOException {
        if (args.length > 0) {
            return Path.of(args[0]);
        }

        for (Path folder : List.of(
                Path.of("Data"),
                Path.of("TFP1", "Data"),
                Path.of("AirlineReliabilityStarter", "TFP1", "Data")
        )) {
            if (Files.isRegularFile(folder.resolve("T_ONTIME_REPORTING.csv"))
                    && Files.isRegularFile(folder.resolve("feb.csv"))
                    && Files.isRegularFile(folder.resolve("march.csv"))) {
                return folder;
            }
        }

        throw new IOException(
                "Could not find a Data folder containing "
                        + "T_ONTIME_REPORTING.csv, feb.csv, and march.csv."
                        + "\nWorking directory: " + Path.of("").toAbsolutePath()
        );
    }

    private static Path requireFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("CSV not found: " + file.toAbsolutePath());
        }
        return file;
    }

    private static void readFlights(Path file, Consumer<Flight> process)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Empty CSV: " + file.toAbsolutePath());
            }

            String[] names = header.replace("\uFEFF", "").split(",", -1);
            Map<String, Integer> columns = new HashMap<>();

            for (int i = 0; i < names.length; i++) {
                columns.put(code(names[i]), i);
            }

            int carrier = column(columns, "OP_UNIQUE_CARRIER", file);
            int origin = column(columns, "ORIGIN", file);
            int dest = column(columns, "DEST", file);
            int departure = column(columns, "CRS_DEP_TIME", file);
            int weekday = column(columns, "DAY_OF_WEEK", file);
            int cancelled = column(columns, "CANCELLED", file);
            int diverted = column(columns, "DIVERTED", file);
            int late = column(columns, "ARR_DEL15", file);

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                // This reader expects the selected BTS columns with no
                // embedded commas in the field values.
                String[] row = line.split(",", -1);
                if (row.length != names.length) {
                    throw new IOException("Unexpected number of fields in "
                            + file + " at line " + lineNumber);
                }

                String airline = code(row[carrier]);
                String from = code(row[origin]);
                String to = code(row[dest]);
                int time = readInt(row[departure]);
                int day = readInt(row[weekday]);
                int cancelFlag = readInt(row[cancelled]);

                if (airline.isEmpty() || from.isEmpty() || to.isEmpty()
                        || time < 0 || time > 2400 || time % 100 >= 60
                        || day < 1 || day > 7 || !isFlag(cancelFlag)) {
                    continue;
                }

                process.accept(new Flight(
                        airline, from, to, (time / 100) % 24, day,
                        cancelFlag, readInt(row[diverted]), readInt(row[late])
                ));
            }
        }
    }

    private static int column(Map<String, Integer> columns,
                              String name, Path file) throws IOException {
        Integer index = columns.get(name);
        if (index == null) {
            throw new IOException("Missing column " + name + " in "
                    + file.toAbsolutePath());
        }
        return index;
    }

    private static String clean(String value) {
        return value.replace("\"", "").trim();
    }

    private static String code(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private static int readInt(String value) {
        try {
            double number = Double.parseDouble(clean(value));
            if (!Double.isFinite(number) || number != Math.rint(number)
                    || number < 0 || number > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) number;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isFlag(int value) {
        return value == 0 || value == 1;
    }

    private record Flight(
            String airline, String origin, String destination,
            int hour, int weekday, int cancelled, int diverted, int late
    ) {
        boolean hasDelayLabel() {
            return cancelled == 0 && diverted == 0 && isFlag(late);
        }

        double[] features() {
            return predictionFeatures(airline, origin, destination, hour, weekday);
        }
    }

    private record Example(Flight flight, int label) {
    }

    private static class TrainingSample {
        private final List<Example> examples = new ArrayList<>();
        private final Random random = new Random(42);
        private int count;
        private int events;

        void add(Flight flight, int label) {
            count++;
            events += label;

            // Sample across the full month, not just the first 50,000 rows.
            if (examples.size() < TRAINING_LIMIT) {
                examples.add(new Example(flight, label));
            } else {
                int index = random.nextInt(count);
                if (index < TRAINING_LIMIT) {
                    examples.set(index, new Example(flight, label));
                }
            }
        }

        double eventRate() {
            return (double) events / count;
        }

        LogisticRegression fit(String name) {
            double[][] features = new double[examples.size()][];
            int[] labels = new int[examples.size()];
            int positiveLabels = 0;

            for (int i = 0; i < examples.size(); i++) {
                Example example = examples.get(i);
                features[i] = example.flight().features();
                labels[i] = example.label();
                positiveLabels += labels[i];
            }

            if (positiveLabels == 0 || positiveLabels == labels.length) {
                throw new IllegalStateException(name
                        + " needs both positive and negative training examples.");
            }

            LogisticRegression model = LogisticRegression.fit(features, labels);

            System.out.printf(
                    "%s: trained on %,d of %,d eligible flights"
                            + " | January event rate: %.2f%%%n",
                    name, labels.length, count, 100 * eventRate()
            );

            return model;
        }
    }

    private static class Score {
        private int count;
        private int events;
        private double modelError;

        void add(double probability, int actual) {
            double difference = probability - actual;
            modelError += difference * difference;
            count++;
            events += actual;
        }

        void print(String name) {
            if (count == 0) {
                System.out.println(name + ": no eligible flights.");
                return;
            }

            System.out.printf(
                    "%s: %,d flights | actual rate: %.2f%% | model Brier: %.5f%n",
                    name, count, 100.0 * events / count, modelError / count
            );
        }
    }
}