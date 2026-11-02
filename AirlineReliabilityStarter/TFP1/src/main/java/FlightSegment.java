import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;

public class FlightSegment {

    private final String marketingAirlineCode;
    private final String operatingAirlineCode;
    private final String flightNumber;
    private final String departureAirport;
    private final String arrivalAirport;
    private final ZonedDateTime departureTime;
    private final ZonedDateTime arrivalTime;

    public FlightSegment(
            String marketingAirlineCode,
            String operatingAirlineCode,
            String flightNumber,
            String departureAirport,
            String arrivalAirport,
            ZonedDateTime departureTime,
            ZonedDateTime arrivalTime) {

        this.marketingAirlineCode = checkAirlineCode(
                marketingAirlineCode, false
        );

        // Operating airline can be unknown. Do not assume it is
        // the same as the marketing airline when matching BTS data.
        this.operatingAirlineCode = checkAirlineCode(
                operatingAirlineCode, true
        );

        this.flightNumber =
                flightNumber == null ? "" : flightNumber.trim();

        this.departureAirport = checkAirport(departureAirport);
        this.arrivalAirport = checkAirport(arrivalAirport);

        if (this.departureAirport.equals(this.arrivalAirport)) {
            throw new IllegalArgumentException(
                    "Departure and arrival airports must be different."
            );
        }

        if (departureTime == null || arrivalTime == null) {
            throw new IllegalArgumentException(
                    "Departure and arrival times are required."
            );
        }

        // AI helped me compare actual instants instead of local clock times.
        // This accounts for flights crossing time zones or midnight.
        if (!arrivalTime.toInstant().isAfter(departureTime.toInstant())) {
            throw new IllegalArgumentException(
                    "Arrival must be after departure."
            );
        }

        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }

    private static String checkAirport(String airport) {
        if (airport == null) {
            throw new IllegalArgumentException(
                    "Airport code is required."
            );
        }

        String code = airport.trim().toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Airport codes must contain three letters."
            );
        }

        // Checks format only, not whether the airport exists.
        return code;
    }

    private static String checkAirlineCode(
            String airlineCode, boolean optional) {

        String code = airlineCode == null
                ? ""
                : airlineCode.trim().toUpperCase(Locale.ROOT);

        if (optional && code.isEmpty()) {
            return "";
        }

        if (!code.matches("[A-Z0-9]{2}")) {
            throw new IllegalArgumentException(
                    "Airline codes must contain two letters or numbers."
            );
        }

        return code;
    }

    public String getMarketingAirlineCode() {
        return marketingAirlineCode;
    }

    public String getOperatingAirlineCode() {
        return operatingAirlineCode;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getDepartureAirport() {
        return departureAirport;
    }

    public String getArrivalAirport() {
        return arrivalAirport;
    }

    public ZonedDateTime getDepartureTime() {
        return departureTime;
    }

    public ZonedDateTime getArrivalTime() {
        return arrivalTime;
    }

    public long getDurationMinutes() {
        return Duration.between(
                departureTime.toInstant(),
                arrivalTime.toInstant()
        ).toMinutes();
    }

    @Override
    public String toString() {
        String flight = marketingAirlineCode;

        if (!flightNumber.isEmpty()) {
            flight += " " + flightNumber;
        }

        return flight + ": "
                + departureAirport + " -> " + arrivalAirport
                + " (" + getDurationMinutes() + " minutes)";
    }
}
