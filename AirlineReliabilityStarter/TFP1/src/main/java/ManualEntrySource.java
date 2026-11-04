import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

public class ManualEntrySource {

    public FlightSegment createSegment(
            String marketingAirlineCode,
            String operatingAirlineCode,
            String flightNumber,
            String departureAirport,
            String arrivalAirport,
            String departureDateTime,
            String departureTimeZone,
            String arrivalDateTime,
            String arrivalTimeZone) {

        ZonedDateTime departure = readTime(
                departureDateTime,
                departureTimeZone
        );

        ZonedDateTime arrival = readTime(
                arrivalDateTime,
                arrivalTimeZone
        );

        // FlightSegment checks airport/code formats and time order.
        // Leave the operating airline blank if it is unknown.
        return new FlightSegment(
                marketingAirlineCode,
                operatingAirlineCode,
                flightNumber,
                departureAirport,
                arrivalAirport,
                departure,
                arrival
        );
    }

    public Itinerary createItinerary(
            String price,
            String currency,
            List<FlightSegment> segments) {

        if (price == null || price.isBlank()) {
            throw new IllegalArgumentException("Enter a ticket price.");
        }

        BigDecimal ticketPrice;

        try {
            // Enter numbers such as 295.50 without a dollar sign.
            ticketPrice = new BigDecimal(price.trim());

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Enter a valid price, such as 295.50.",
                    e
            );
        }

        // AI helped the same itinerary class for manual and API
        // inputs, so both can later use the same reliability analysis.
        return new Itinerary(
                ticketPrice,
                currency,
                segments,
                Itinerary.Source.MANUAL
        );
    }

    private ZonedDateTime readTime(
            String dateTimeText,
            String timeZoneText) {

        if (dateTimeText == null || dateTimeText.isBlank()) {
            throw new IllegalArgumentException(
                    "Enter a departure and arrival date/time."
            );
        }

        if (timeZoneText == null || timeZoneText.isBlank()) {
            throw new IllegalArgumentException(
                    "Enter the airport's time zone."
            );
        }

        LocalDateTime localTime;
        ZoneId zone;

        try {
            localTime = LocalDateTime.parse(dateTimeText.trim());
            zone = ZoneId.of(timeZoneText.trim());

        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException(
                    "Use a date/time like 2026-11-25T08:00 "
                            + "and a zone like America/Chicago.",
                    e
            );
        }

        // AI helped check daylight-saving transitions instead of
        // silently adjusting a nonexistent or ambiguous local time.
        List<ZoneOffset> validOffsets =
                zone.getRules().getValidOffsets(localTime);

        if (validOffsets.isEmpty()) {
            throw new IllegalArgumentException(
                    "This local time does not exist because "
                            + "of a daylight-saving clock change."
            );
        }

        if (validOffsets.size() > 1) {
            throw new IllegalArgumentException(
                    "This local time occurs twice during a "
                            + "daylight-saving clock change. "
                            + "Enter UTC time with zone UTC instead."
            );
        }

        return ZonedDateTime.ofStrict(
                localTime,
                validOffsets.get(0),
                zone
        );
    }
}
