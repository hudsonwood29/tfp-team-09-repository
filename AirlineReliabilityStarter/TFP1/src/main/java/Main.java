import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class Main {

    public static void main(String[] args) throws IOException {

        Path file;

        if (args.length > 0) {
            file = Path.of(args[0]);
        } else {
            file = Path.of("data", "T_ONTIME_REPORTING.csv");

            // AI helped me check both possible working directories.
            // The second path matches the folder structure on my computer.
            if (!Files.isRegularFile(file)) {
                file = Path.of(
                        "AirlineReliabilityStarter",
                        "TFP1",
                        "Data",
                        "T_ONTIME_REPORTING.csv"
                );
            }
        }

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "CSV not found: " + file.toAbsolutePath()
                            + "\nCheck the CSV filename and folder location."
            );
        }

        ReliabilityStats overall = new ReliabilityStats();

        Map<String, ReliabilityStats> airlines = new TreeMap<>();

        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {

            String header = reader.readLine();

            if (header == null) {
                throw new IOException("The CSV is empty.");
            }

            List<String> headings =
                    parseCsvLine(header.replace("\uFEFF", ""));

            // Find columns by their names instead of fixed positions.
            Map<String, Integer> columns = new HashMap<>();

            for (int i = 0; i < headings.size(); i++) {
                columns.put(headings.get(i).trim(), i);
            }

            String[] required = {
                    "FL_DATE",
                    "OP_UNIQUE_CARRIER",
                    "ORIGIN",
                    "DEST",
                    "CANCELLED",
                    "DIVERTED",
                    "ARR_DELAY"
            };

            for (String name : required) {
                if (!columns.containsKey(name)) {
                    throw new IOException(
                            "Required column is missing: " + name
                    );
                }
            }

            String line;
            long lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                try {
                    List<String> row = parseCsvLine(line);

                    if (row.size() != headings.size()) {
                        throw new IllegalArgumentException(
                                "Unexpected column count"
                        );
                    }

                    String airline =
                            value(row, columns, "OP_UNIQUE_CARRIER");

                    if (airline.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Missing airline"
                        );
                    }

                    boolean cancelled =
                            flag(value(row, columns, "CANCELLED"));

                    boolean diverted =
                            flag(value(row, columns, "DIVERTED"));

                    String delayText =
                            value(row, columns, "ARR_DELAY");

                    // AI helped me preserve missing delays as null instead
                    // of treating them as zero-minute delays.
                    Double delay = null;

                    if (!delayText.isEmpty()) {
                        delay = Double.valueOf(delayText);
                    }

                    if (delay != null && !Double.isFinite(delay)) {
                        throw new IllegalArgumentException(
                                "Non-finite arrival delay"
                        );
                    }

                    overall.addFlight(cancelled, diverted, delay);

                    ReliabilityStats airlineStats = airlines.get(airline);

                    if (airlineStats == null) {
                        airlineStats = new ReliabilityStats();
                        airlines.put(airline, airlineStats);
                    }

                    airlineStats.addFlight(cancelled, diverted, delay);

                } catch (RuntimeException e) {
                    throw new IOException(
                            "Invalid CSV record on line " + lineNumber,
                            e
                    );
                }
            }
        }

        System.out.println(
                "BTS HISTORICAL RELIABILITY - INITIAL PROTOTYPE"
        );

        System.out.println("Source: " + file.toAbsolutePath());

        printStats("All airlines", overall);

        System.out.println();
        System.out.println("BY OPERATING AIRLINE CODE");

        for (Map.Entry<String, ReliabilityStats> entry
                : airlines.entrySet()) {

            printStats(entry.getKey(), entry.getValue());
        }

        System.out.println();
        System.out.println(
                "Late means arrival delay of 15 minutes or more."
        );
        System.out.println(
                "Arrival metrics exclude cancelled/diverted flights "
                        + "and missing delays."
        );
        System.out.println(
                "Mean delay includes negative values for early arrivals."
        );
        System.out.println(
                "These are historical statistics, not future-flight predictions."
        );
    }

    private static void printStats(
            String name, ReliabilityStats stats) {

        System.out.printf(
                Locale.US,
                "%n%s%n"
                        + "Total flights: %d%n"
                        + "Cancelled flights: %d%n"
                        + "Cancellation rate: %.2f%%%n"
                        + "Valid arrival records: %d%n"
                        + "Late-arrival rate: %.2f%%%n"
                        + "Mean arrival delay: %.2f minutes%n",
                name,
                stats.getFlights(),
                stats.getCancelled(),
                stats.cancellationPercent(),
                stats.getArrivalRecords(),
                stats.lateArrivalPercent(),
                stats.averageArrivalDelay()
        );
    }

    private static String value(
            List<String> row,
            Map<String, Integer> columns,
            String name) {

        return row.get(columns.get(name)).trim();
    }

    private static boolean flag(String text) {
        double number = Double.parseDouble(text);

        if (number != 0 && number != 1) {
            throw new IllegalArgumentException(
                    "Expected 0 or 1, got " + text
            );
        }

        return number == 1;
    }

    // AI helped write this parser so commas inside quoted fields
    // do not incorrectly split a CSV record.
    // It supports the BTS file's single-line records, not multiline fields.
    static List<String> parseCsvLine(String line) {

        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);

            if (character == '"') {

                if (quoted
                        && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {

                    field.append('"');
                    i++;

                } else {
                    quoted = !quoted;
                }

            } else if (character == ',' && !quoted) {

                fields.add(field.toString());
                field.setLength(0);

            } else {
                field.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException(
                    "Unclosed quoted field"
            );
        }

        fields.add(field.toString());

        return fields;
    }
}