public class ReliabilityStats {
    private long flights;
    private long cancelled;
    private long arrivalRecords;
    private long lateArrivals;
    private double arrivalDelayTotal;

    public void addFlight(boolean isCancelled, boolean isDiverted, Double delay) {
        flights++;
        if (isCancelled) {
            cancelled++;
        }
        // AI helped separate cancellation counts from valid arrival observations.
        // A missing delay must not remove a cancelled flight from the denominator.
        if (!isCancelled && !isDiverted && delay != null) {
            arrivalRecords++;
            arrivalDelayTotal += delay;
            if (delay >= 15) {
                lateArrivals++;
            }
        }
    }

    public long getFlights() { return flights; }
    public long getCancelled() { return cancelled; }
    public long getArrivalRecords() { return arrivalRecords; }

    public double cancellationPercent() {
        return flights == 0 ? Double.NaN : 100.0 * cancelled / flights;
    }

    public double lateArrivalPercent() {
        return arrivalRecords == 0 ? Double.NaN : 100.0 * lateArrivals / arrivalRecords;
    }

    public double averageArrivalDelay() {
        return arrivalRecords == 0 ? Double.NaN : arrivalDelayTotal / arrivalRecords;
    }
}
