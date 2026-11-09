import smile.classification.LogisticRegression;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class ModelTraining {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 1, 1);
    private static final LocalDate TEST_START = LocalDate.of(2026, 1, 25);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 1, 31);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M/d/uuuu");

    private static final int TRAINING_LIMIT = 50_000;
    private static final int AIRPORT_LIMIT = 20;

    private record Flight(LocalDate date, String carrier, String origin,
                          String destination, int hour, int late) {}

    public static void main(String[] args) throws IOException {
        Path file = findCsv(args);

        List<Flight> training = new ArrayList<>();
        List<Flight> testing = new ArrayList<>();
        readFlights(file, training, testing);

        if (training.isEmpty() || testing.isEmpty()) {
            throw new IllegalStateException("Both date groups need usable flights.");
        }

        printGroup("Training", training);
        printGroup("Testing", testing);

        // AI helped me keep the test dates out of feature setup and model training.
        Map<String, Integer> carriers = new HashMap<>();
        Map<String, Integer> airportCounts = new HashMap<>();

        for (Flight flight : training) {
            carriers.computeIfAbsent(flight.carrier(), key -> carriers.size());
            airportCounts.merge(flight.origin(), 1, Integer::sum);
            airportCounts.merge(flight.destination(), 1, Integer::sum);
        }

        Map<String, Integer> airports = new HashMap<>();
        List<String> busiest = airportCounts.keySet().stream()
                .sorted((a, b) -> {
                    int byCount = Integer.compare(airportCounts.get(b), airportCounts.get(a));
                    return byCount != 0 ? byCount : a.compareTo(b);
                })
                .limit(AIRPORT_LIMIT)
                .toList();

        for (String airport : busiest) {
            airports.put(airport, airports.size());
        }

        double baseline = training.stream().mapToInt(Flight::late).average().orElseThrow();

        // Shuffle only the training rows. The newest dates stay untouched for testing.
        List<Flight> sample = new ArrayList<>(training);
        java.util.Collections.shuffle(sample, new Random(42));
        int sampleSize = Math.min(TRAINING_LIMIT, sample.size());

        double[][] x = new double[sampleSize][];
        int[] y = new int[sampleSize];

        for (int i = 0; i < sampleSize; i++) {
            Flight flight = sample.get(i);
            x[i] = features(flight, carriers, airports);
            y[i] = flight.late();
        }

        System.out.println("Fitting SMILE model on " + sampleSize + " training flights...");
        LogisticRegression model = LogisticRegression.binomial(x, y);

        double baselineError = 0;
        double modelError = 0;
        double[] probabilities = new double[2];

        for (Flight flight : testing) {
            model.predict(features(flight, carriers, airports), probabilities);

            // The classes are 0 = not late, 1 = late.
            double modelDifference = probabilities[1] - flight.late();
            double baselineDifference = baseline - flight.late();

            modelError += modelDifference * modelDifference;
            baselineError += baselineDifference * baselineDifference;
        }

        System.out.printf(Locale.US, "%nTraining late rate: %.2f%%%n", baseline * 100);
        System.out.printf(Locale.US, "Baseline test Brier score: %.5f%n",
                baselineError / testing.size());
        System.out.printf(Locale.US, "SMILE test Brier score:    %.5f%n",
                modelError / testing.size());
        System.out.println("Lower Brier score is better.");
    }

    private static void readFlights(Path file, List<Flight> training,
                                    List<Flight> testing) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("The BTS CSV is empty.");
            }

            List<String> names = Arrays.asList(header.replace("\uFEFF", "").split(","));
            int dateCol = column(names, "FL_DATE");
            int carrierCol = column(names, "OP_UNIQUE_CARRIER");
            int originCol = column(names, "ORIGIN");
            int destCol = column(names, "DEST");
            int timeCol = column(names, "CRS_DEP_TIME");
            int lateCol = column(names, "ARR_DEL15");
            int cancelledCol = column(names, "CANCELLED");
            int divertedCol = column(names, "DIVERTED");

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;

                // The supplied BTS export uses unquoted fields. Keep empty fields.
                String[] row = line.split(",", -1);

                try {
                    if (row.length != names.size()) {
                        throw new IllegalArgumentException("Unexpected number of columns.");
                    }

                    if (!isZero(row[cancelledCol]) || !isZero(row[divertedCol])
                            || row[lateCol].isBlank() || row[timeCol].isBlank()) {
                        continue;
                    }

                    // FL_DATE looks like "1/5/2026 12:00:00 AM" in this export.
                    String dateText = row[dateCol].trim().split("\\s+", 2)[0];
                    LocalDate date = LocalDate.parse(dateText, DATE_FORMAT);
                    if (date.isBefore(FIRST_DAY) || date.isAfter(LAST_DAY)) continue;

                    int time = Integer.parseInt(row[timeCol].trim());
                    if (time != 2400 && (time < 0 || time / 100 > 23 || time % 100 > 59)) {
                        throw new IllegalArgumentException("Invalid scheduled departure time.");
                    }
                    int hour = (time / 100) % 24;

                    double outcome = Double.parseDouble(row[lateCol]);
                    if (outcome != 0 && outcome != 1) {
                        throw new IllegalArgumentException("ARR_DEL15 must be 0 or 1.");
                    }

                    Flight flight = new Flight(date, row[carrierCol].trim(),
                            row[originCol].trim(), row[destCol].trim(), hour, (int) outcome);

                    if (date.isBefore(TEST_START)) {
                        training.add(flight);
                    } else {
                        testing.add(flight);
                    }
                } catch (RuntimeException e) {
                    throw new IOException("Could not read BTS CSV line " + lineNumber, e);
                }
            }
        }
    }

    private static double[] features(Flight flight, Map<String, Integer> carriers,
                                     Map<String, Integer> airports) {
        int carrierSlots = carriers.size() + 1;
        int airportSlots = airports.size() + 1;
        double[] values = new double[24 + 7 + carrierSlots + 2 * airportSlots];

        // Put a 1 in the matching category instead of treating codes as numbers.
        values[flight.hour()] = 1;
        values[24 + flight.date().getDayOfWeek().getValue() - 1] = 1;

        int start = 31;
        values[start + carriers.getOrDefault(flight.carrier(), carriers.size())] = 1;
        start += carrierSlots;
        values[start + airports.getOrDefault(flight.origin(), airports.size())] = 1;
        start += airportSlots;
        values[start + airports.getOrDefault(flight.destination(), airports.size())] = 1;

        return values;
    }

    private static boolean isZero(String value) {
        return !value.isBlank() && Double.parseDouble(value.trim()) == 0;
    }

    private static int column(List<String> names, String name) throws IOException {
        int index = names.indexOf(name);
        if (index < 0) throw new IOException("Missing column: " + name);
        return index;
    }

    private static void printGroup(String name, List<Flight> flights) {
        LocalDate earliest = flights.get(0).date();
        LocalDate latest = earliest;
        for (Flight flight : flights) {
            if (flight.date().isBefore(earliest)) earliest = flight.date();
            if (flight.date().isAfter(latest)) latest = flight.date();
        }
        System.out.printf("%s: %,d flights (%s to %s)%n",
                name, flights.size(), earliest, latest);
    }

    private static Path findCsv(String[] args) throws IOException {
        if (args.length > 0) {
            Path path = Path.of(args[0]);
            if (Files.isRegularFile(path)) return path;
            throw new IOException("CSV not found: " + path.toAbsolutePath());
        }

        for (Path folder : List.of(Path.of(""), Path.of("TFP1"),
                Path.of("AirlineReliabilityStarter", "TFP1"))) {
            for (String dataFolder : List.of("data", "Data")) {
                Path path = folder.resolve(dataFolder).resolve("T_ONTIME_REPORTING.csv");
                if (Files.isRegularFile(path)) return path;
            }
        }

        throw new IOException("Put T_ONTIME_REPORTING.csv in TFP1/data, "
                + "or pass the CSV's full path as a program argument.");
    }
}
