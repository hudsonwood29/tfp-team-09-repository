import smile.classification.LogisticRegression;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class RouteDelayPredictor {

    private final LogisticRegression model;
    private final Set<String> routes;

    private RouteDelayPredictor(
            LogisticRegression model,
            Set<String> routes
    ) {
        this.model = model;
        this.routes = Set.copyOf(routes);
    }

    public static RouteDelayPredictor train(Path csv) throws IOException {
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        Set<String> routes = new HashSet<>();
        Random random = new Random(42);

        int eligible = 0;

        try (BufferedReader reader = Files.newBufferedReader(csv)) {
            String header = reader.readLine();

            if (header == null) {
                throw new IOException("The BTS file is empty.");
            }

            String[] names = header.replace("\uFEFF", "").split(",", -1);
            Map<String, Integer> columns = new HashMap<>();

            for (int i = 0; i < names.length; i++) {
                columns.put(clean(names[i]), i);
            }

            int originIndex = required(columns, "ORIGIN");
            int destinationIndex = required(columns, "DEST");
            int departureIndex = required(columns, "CRS_DEP_TIME");
            int weekdayIndex = required(columns, "DAY_OF_WEEK");
            int cancelledIndex = required(columns, "CANCELLED");
            int divertedIndex = required(columns, "DIVERTED");
            int lateIndex = required(columns, "ARR_DEL15");

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] values = line.split(",", -1);

                if (values.length != names.length) {
                    throw new IOException("Unexpected CSV field count.");
                }

                int cancelled = number(values[cancelledIndex]);
                int diverted = number(values[divertedIndex]);
                int late = number(values[lateIndex]);
                int time = number(values[departureIndex]);
                int weekday = number(values[weekdayIndex]);

                String origin = clean(values[originIndex]);
                String destination = clean(values[destinationIndex]);

                if (cancelled != 0 || diverted != 0
                        || (late != 0 && late != 1)
                        || time < 0 || time > 2400 || time % 100 >= 60
                        || weekday < 1 || weekday > 7
                        || !origin.matches("[A-Z]{3}")
                        || !destination.matches("[A-Z]{3}")) {
                    continue;
                }

                routes.add(origin + "-" + destination);
                eligible++;

                double[] x = features(
                        origin, destination, (time / 100) % 24, weekday
                );

                // AI helped me sample across January instead of only
                // taking the first 50,000 records in the CSV.
                if (features.size() < 50_000) {
                    features.add(x);
                    labels.add(late);
                } else {
                    int index = random.nextInt(eligible);

                    if (index < 50_000) {
                        features.set(index, x);
                        labels.set(index, late);
                    }
                }
            }
        }

        if (!labels.contains(0) || !labels.contains(1)) {
            throw new IOException(
                    "Training needs both late and non-late flights."
            );
        }

        double[][] x = features.toArray(new double[0][]);
        int[] y = labels.stream().mapToInt(Integer::intValue).toArray();

        System.out.printf(
                "Training route/time fallback on %,d of %,d flights.%n",
                y.length, eligible
        );

        return new RouteDelayPredictor(
                LogisticRegression.fit(x, y), routes
        );
    }

    public Result predict(FlightSegment segment) {
        if (segment == null) {
            return new Result(null, "Flight segment is missing.");
        }

        String route = segment.getDepartureAirport()
                + "-" + segment.getArrivalAirport();

        if (!routes.contains(route)) {
            return new Result(null, "Route was not present in training data.");
        }

        // Our mapper uses UTC as a fallback when the local zone is absent.
        // For the domestic scope, reject this rather than use the wrong hour.
        if (segment.getDepartureTime().getZone().normalized()
                .equals(ZoneOffset.UTC)) {
            return new Result(null, "Local departure time zone is unavailable.");
        }

        var departure = segment.getDepartureTime();

        double[] x = features(
                segment.getDepartureAirport(),
                segment.getArrivalAirport(),
                departure.getHour(),
                departure.getDayOfWeek().getValue()
        );

        double[] probabilities = new double[2];
        model.predict(x, probabilities);

        return new Result(
                probabilities[1],
                "Experimental route/time estimate; airline is not included."
        );
    }

    private static double[] features(
            String origin, String destination, int hour, int weekday
    ) {
        // Use the existing encoding with the same fixed carrier token
        // for every training and prediction row. Airline has no variation.
        return BTSMonthlyTraining.predictionFeatures(
                "ROUTE_ONLY", origin, destination, hour, weekday
        );
    }

    private static int required(Map<String, Integer> columns, String name)
            throws IOException {
        Integer index = columns.get(name);

        if (index == null) {
            throw new IOException("Missing BTS column: " + name);
        }

        return index;
    }

    private static String clean(String value) {
        return value.replace("\"", "").trim().toUpperCase(Locale.ROOT);
    }

    private static int number(String value) {
        try {
            double result = Double.parseDouble(clean(value));

            if (!Double.isFinite(result)
                    || result != Math.rint(result)
                    || result < 0 || result > Integer.MAX_VALUE) {
                return -1;
            }

            return (int) result;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public record Result(Double probability, String message) {
        public boolean available() {
            return probability != null;
        }
    }
}