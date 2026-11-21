import java.time.ZoneId;
import java.util.Locale;

/**
 * One airport from airports.csv.
 *
 * code      three-letter code used by BTS and Ignav, e.g. LIT
 * name      full airport name, e.g. Bill and Hillary Clinton National Airport
 * city      city the airport serves, e.g. Little Rock
 * region    state code for US airports (AR), territory code (PR), or country code
 * country   two-letter country code
 * timeZone  the airport's time zone, e.g. America/Chicago
 * inBtsData true when the airport appears in our BTS on-time CSVs
 */
public record Airport(
        String code,
        String name,
        String city,
        String region,
        String country,
        ZoneId timeZone,
        boolean inBtsData
) {

    // "Little Rock, AR"
    public String cityAndRegion() {
        if (region.isBlank()) {
            return city;
        }
        return city + ", " + region;
    }

    // "Little Rock, AR (LIT)" - short enough to fit in a text box.
    public String shortLabel() {
        return cityAndRegion() + " (" + code + ")";
    }

    // "Bill and Hillary Clinton National Airport (LIT), Little Rock, AR"
    public String fullLabel() {
        return name + " (" + code + "), " + cityAndRegion();
    }

    // AI helped me let users search by code, city, or airport name,
    // so they do not need to know the three-letter code.
    public boolean matches(String typed) {
        if (typed == null || typed.isBlank()) {
            return true;
        }

        String text = typed.trim().toLowerCase(Locale.ROOT);

        return code.toLowerCase(Locale.ROOT).contains(text)
                || city.toLowerCase(Locale.ROOT).contains(text)
                || name.toLowerCase(Locale.ROOT).contains(text);
    }
}
