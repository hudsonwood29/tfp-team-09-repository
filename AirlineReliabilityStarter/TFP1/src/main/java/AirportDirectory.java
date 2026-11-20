import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns three-letter airport codes into names people recognize.
 *
 *   AirportDirectory airports = AirportDirectory.shared();
 *   airports.describe("LIT");  // "Bill and Hillary Clinton National Airport (LIT), Little Rock, AR"
 *   airports.shortName("LIT"); // "Little Rock, AR (LIT)"
 *   airports.timeZone("DFW");  // America/Chicago
 *
 * The data is in src/main/resources/airports.csv.
 */
public class AirportDirectory {

    private static final String FILE_NAME = "/airports.csv";

    // One copy for the whole program, loaded the first time it is needed.
    private static AirportDirectory shared;

    private final Map<String, Airport> byCode = new HashMap<>();
    private final List<Airport> btsAirports = new ArrayList<>();

    public static AirportDirectory shared() {
        if (shared == null) {
            shared = new AirportDirectory();
        }
        return shared;
    }

    private AirportDirectory() {
        // AI helped me read the CSV from the resources folder, so it is found
        // no matter which folder the program is started from.
        InputStream file = AirportDirectory.class.getResourceAsStream(FILE_NAME);

        if (file == null) {
            throw new IllegalStateException(
                    "Missing airports.csv. Put it in src/main/resources.");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file, StandardCharsets.UTF_8))) {

            reader.readLine(); // skip the header

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                // Airport names can contain commas, so use the quote-aware
                // parser from Main instead of split(",").
                List<String> row = Main.parseCsvLine(line);

                Airport airport = new Airport(
                        row.get(0).trim().toUpperCase(Locale.ROOT),
                        row.get(1).trim(),
                        row.get(2).trim(),
                        row.get(3).trim(),
                        row.get(4).trim(),
                        ZoneId.of(row.get(5).trim()),
                        Boolean.parseBoolean(row.get(6).trim())
                );

                byCode.put(airport.code(), airport);

                if (airport.inBtsData()) {
                    btsAirports.add(airport);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Sort by city so the drop-down list is in alphabetical order.
        btsAirports.sort(Comparator.comparing(Airport::cityAndRegion));
    }

    // Returns null when the code is not in the file.
    public Airport find(String code) {
        if (code == null) {
            return null;
        }
        return byCode.get(code.trim().toUpperCase(Locale.ROOT));
    }

    // Full name for display. Unknown codes are shown as-is, never blank.
    public String describe(String code) {
        Airport airport = find(code);
        return airport == null ? code : airport.fullLabel();
    }

    // "Little Rock, AR (LIT)", or the code itself when unknown.
    public String shortName(String code) {
        Airport airport = find(code);
        return airport == null ? code : airport.shortLabel();
    }

    // The airport's time zone, or null when unknown. Manual entry could use
    // this so users never have to type "America/Chicago" themselves.
    public ZoneId timeZone(String code) {
        Airport airport = find(code);
        return airport == null ? null : airport.timeZone();
    }

    // The 346 airports that appear in our BTS data, sorted by city.
    public List<Airport> btsAirports() {
        return btsAirports;
    }
}
