import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class IgnavMapper {

    // Reads the IGNAV response and adds each trip to the list.
    // Same pattern as readProblemData in the CD06 KPSolver demo:
    // main creates an empty ArrayList and this method fills it.
    // Field names come from https://ignav.com/docs/response-format
    public static void readIgnavData(
            String responseBody,
            ArrayList<Itinerary> itineraries) {

        JsonObject root = JsonParser.parseString(
                responseBody
        ).getAsJsonObject();

        JsonElement list = root.get("itineraries");

        // No itineraries means no flights were found.
        // The list stays empty.
        if (list == null || !list.isJsonArray()) {
            return;
        }

        int skipped = 0;

        for (JsonElement element : list.getAsJsonArray()) {

            // AI helped me skip one bad itinerary instead of stopping
            // the whole search. FlightSegment and Itinerary throw
            // IllegalArgumentException when data is missing or invalid.
            try {
                Itinerary itinerary =
                        toItinerary(element.getAsJsonObject());

                itineraries.add(itinerary);

            } catch (RuntimeException e) {
                skipped++;
            }
        }

        if (skipped > 0) {
            System.out.println(
                    "Skipped " + skipped
                            + " itinerary(ies) with missing or invalid data."
            );
        }
    }

    private static Itinerary toItinerary(JsonObject json) {

        JsonObject price = json.getAsJsonObject("price");
        JsonObject outbound = json.getAsJsonObject("outbound");
        JsonArray segmentArray = outbound.getAsJsonArray("segments");

        List<FlightSegment> segments = new ArrayList<>();

        for (JsonElement element : segmentArray) {
            FlightSegment segment = toSegment(element.getAsJsonObject());
            segments.add(segment);
        }

        // BigDecimal keeps the exact price instead of a rounded double.
        BigDecimal amount = price.get("amount").getAsBigDecimal();

        return new Itinerary(
                amount,
                text(price, "currency"),
                segments,
                Itinerary.Source.IGNAV
        );
    }

    private static FlightSegment toSegment(JsonObject json) {

        ZonedDateTime departure = zonedTime(
                json,
                "departure_time_local",
                "departure_timezone",
                "departure_time_utc"
        );

        ZonedDateTime arrival = zonedTime(
                json,
                "arrival_time_local",
                "arrival_timezone",
                "arrival_time_utc"
        );

        // IGNAV gives the operating airline's name, not its 2-letter code,
        // so the operating code is left blank. FlightSegment allows this.
        // The marketing code is not copied here because regional partners
        // (for example, SkyWest flying as UA) would match the wrong BTS data.
        return new FlightSegment(
                text(json, "marketing_carrier_code"),
                "",
                text(json, "flight_number"),
                text(json, "departure_airport"),
                text(json, "arrival_airport"),
                departure,
                arrival
        );
    }

    // IGNAV can send null or leave a field out, so check before reading it.
    private static String text(JsonObject json, String field) {

        JsonElement value = json.get(field);

        if (value == null || value.isJsonNull()) {
            return null;
        }

        return value.getAsString();
    }

    // AI helped me build ZonedDateTime values from IGNAV's local time
    // and time zone. If the time zone is missing, the UTC time is used.
    // It is the same instant, so durations and layovers stay correct.
    private static ZonedDateTime zonedTime(
            JsonObject json,
            String localField,
            String zoneField,
            String utcField) {

        String local = text(json, localField);
        String zone = text(json, zoneField);

        if (local != null && zone != null) {
            return LocalDateTime.parse(local).atZone(ZoneId.of(zone));
        }

        String utc = text(json, utcField);

        if (utc != null) {
            return Instant.parse(utc).atZone(ZoneOffset.UTC);
        }

        // A local time without a time zone cannot be compared safely.
        throw new IllegalArgumentException(
                "Missing time zone and UTC time for " + localField
        );
    }
}