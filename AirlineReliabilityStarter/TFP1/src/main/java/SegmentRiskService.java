import java.time.ZoneOffset;
import java.util.Objects;

public class SegmentRiskService {

    private final FlightRiskPredictorIgnavandHistorical predictor;

    public SegmentRiskService(
            FlightRiskPredictorIgnavandHistorical predictor
    ) {
        this.predictor = Objects.requireNonNull(predictor);
    }

    public Result predict(FlightSegment segment) {
        if (segment == null) {
            return new Result(null, "Flight segment is missing.");
        }

        String operatingCode = segment.getOperatingAirlineCode();

        if (operatingCode == null || operatingCode.isBlank()) {
            return new Result(
                    null,
                    "Operating carrier is not confirmed."
            );
        }

        // The mapper can fall back to UTC when a local zone is missing.
        // Do not treat UTC as the departure airport's local clock.
        if (segment.getDepartureTime().getZone().normalized()
                .equals(ZoneOffset.UTC)) {
            return new Result(
                    null,
                    "Departure airport's local time zone is not confirmed."
            );
        }

        // AI helped connect the existing flight object to the model
        // without substituting the marketing airline for the operator.
        var risk = predictor.predict(
                operatingCode,
                segment.getDepartureAirport(),
                segment.getArrivalAirport(),
                segment.getDepartureTime().toLocalDateTime()
        );

        return new Result(risk, "Prediction available.");
    }

    public void print(Itinerary itinerary) {
        System.out.println("\n" + itinerary);

        for (FlightSegment segment : itinerary.getSegments()) {
            Result result = predict(segment);

            System.out.println("  " + segment);

            if (!result.available()) {
                System.out.println(
                        "    Delay estimate unavailable: " + result.message()
                );
                continue;
            }

            System.out.printf(
                    "    Estimated chance of arriving 15+ minutes late: %.1f%%%n",
                    result.risk().delayProbability() * 100
            );

            System.out.println(
                    "    Estimate applies if the flight operates "
                            + "and is not diverted."
            );
        }
    }

    public record Result(
            FlightRiskPredictorIgnavandHistorical.FlightRisk risk,
            String message
    ) {
        public boolean available() {
            return risk != null;
        }
    }
}
