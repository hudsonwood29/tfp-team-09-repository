import smile.classification.LogisticRegression;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

public final class FlightRiskPredictorIgnavandHistorical {

    private final LogisticRegression delayModel;
    private final LogisticRegression cancellationModel;
    private final double historicalCancellationRate;

    public FlightRiskPredictorIgnavandHistorical(
            LogisticRegression delayModel,
            LogisticRegression cancellationModel,
            double historicalCancellationRate
    ) {
        this.delayModel = Objects.requireNonNull(delayModel);
        this.cancellationModel = Objects.requireNonNull(cancellationModel);
        this.historicalCancellationRate = historicalCancellationRate;
    }

    public FlightRisk predict(
            String operatingCarrier,
            String origin,
            String destination,
            LocalDateTime localDeparture
    ) {
        Objects.requireNonNull(localDeparture, "Local departure time is required.");

        // AI helped me reuse the training features so Ignav and manual
        // flights are scored the same way as the historical BTS flights.
        double[] features = BTSMonthlyTraining.predictionFeatures(
                normalizeCode(operatingCarrier, "Operating carrier"),
                normalizeCode(origin, "Origin airport"),
                normalizeCode(destination, "Destination airport"),
                localDeparture.getHour(),
                localDeparture.getDayOfWeek().getValue()
        );

        return new FlightRisk(
                probability(delayModel, features),
                probability(cancellationModel, features),
                historicalCancellationRate
        );
    }

    private static double probability(
            LogisticRegression model, double[] features
    ) {
        double[] probabilities = new double[2];
        model.predict(features, probabilities);
        return probabilities[1];
    }

    private static String normalizeCode(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record FlightRisk(
            double delayProbability,
            double experimentalCancellationProbability,
            double historicalCancellationRate
    ) {
    }
}
