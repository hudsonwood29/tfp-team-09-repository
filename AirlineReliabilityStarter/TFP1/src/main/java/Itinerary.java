import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class Itinerary {

    public enum Source {
        MANUAL,
        IGNAV
    }

    private final BigDecimal price;
    private final String currency;
    private final List<FlightSegment> segments;
    private final Source source;

    public Itinerary(
            BigDecimal price,
            String currency,
            List<FlightSegment> segments,
            Source source) {

        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero."
            );
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency is required."
            );
        }

        String currencyCode =
                currency.trim().toUpperCase(Locale.ROOT);

        // Reject unrecognized currency codes.
        Currency.getInstance(currencyCode);

        if (source == null) {
            throw new IllegalArgumentException(
                    "The itinerary source is required."
            );
        }

        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Add at least one flight segment."
            );
        }

        List<FlightSegment> checkedSegments = new ArrayList<>();

        for (FlightSegment segment : segments) {
            if (segment == null) {
                throw new IllegalArgumentException(
                        "A flight segment cannot be null."
                );
            }

            checkedSegments.add(segment);
        }

        // AI helped me validate connections so both input sources
        // create trips with matching airports and valid time order.
        for (int i = 1; i < checkedSegments.size(); i++) {
            FlightSegment previous = checkedSegments.get(i - 1);
            FlightSegment current = checkedSegments.get(i);

            if (!previous.getArrivalAirport()
                    .equals(current.getDepartureAirport())) {

                throw new IllegalArgumentException(
                        "Connecting flights must use the same airport."
                );
            }

            if (!current.getDepartureTime().toInstant()
                    .isAfter(previous.getArrivalTime().toInstant())) {

                throw new IllegalArgumentException(
                        "A connecting flight must depart after "
                                + "the previous flight arrives."
                );
            }
        }

        this.price = price;
        this.currency = currencyCode;
        this.source = source;

        // Protect the itinerary from changes to the original list.
        this.segments = List.copyOf(checkedSegments);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public List<FlightSegment> getSegments() {
        return segments;
    }

    public Source getSource() {
        return source;
    }

    public String getDepartureAirport() {
        return segments.get(0).getDepartureAirport();
    }

    public String getArrivalAirport() {
        return segments.get(segments.size() - 1).getArrivalAirport();
    }

    public int getConnectionCount() {
        return segments.size() - 1;
    }

    public boolean isNonstop() {
        return segments.size() == 1;
    }

    public long getTotalDurationMinutes() {
        FlightSegment first = segments.get(0);
        FlightSegment last = segments.get(segments.size() - 1);

        // Includes flying time and layovers.
        return Duration.between(
                first.getDepartureTime().toInstant(),
                last.getArrivalTime().toInstant()
        ).toMinutes();
    }

    public List<Long> getLayoverMinutes() {
        List<Long> layovers = new ArrayList<>();

        for (int i = 1; i < segments.size(); i++) {
            FlightSegment previous = segments.get(i - 1);
            FlightSegment current = segments.get(i);

            long minutes = Duration.between(
                    previous.getArrivalTime().toInstant(),
                    current.getDepartureTime().toInstant()
            ).toMinutes();

            layovers.add(minutes);
        }

        return List.copyOf(layovers);
    }

    @Override
    public String toString() {
        return getDepartureAirport() + " -> " + getArrivalAirport()
                + " | " + currency + " " + price.toPlainString()
                + " | Connections: " + getConnectionCount()
                + " | Duration: " + getTotalDurationMinutes() + " minutes"
                + " | Source: " + source;
    }
}