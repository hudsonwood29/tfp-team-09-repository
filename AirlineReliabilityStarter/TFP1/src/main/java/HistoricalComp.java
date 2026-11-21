import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.numbers.NumberColumnFormatter;

import java.io.File;
import java.io.IOException;

public class HistoricalComp {

    // A group needs at least this many flights before we compare it.
    // Smaller groups (like a route flown a few times) give percentages
    // that swing a lot from just one or two flights.
    static int MIN_FLIGHTS = 100;

    public static void main(String[] args) throws IOException {

        //******************************************************************************************************************************
        //***Read the BTS files into one Tablesaw Table***

        // Each BTS file is one month, so the season comes from the file.
        String[] fileNames = {"T_ONTIME_REPORTING.csv", "feb.csv", "march.csv"};
        String[] seasons = {"Winter (Dec-Feb)", "Winter (Dec-Feb)", "Spring (Mar-May)"};

        Table flights = null;

        for (int i = 0; i < fileNames.length; i++) {

            File file = new File("Data/" + fileNames[i]);

            if (!file.exists()) {
                System.out.println("Not found, skipping: Data/" + fileNames[i]);
                continue;
            }

            Table month = readMonth("Data/" + fileNames[i], seasons[i]);
            System.out.println("Read " + month.rowCount() + " flights from " + fileNames[i]);

            if (flights == null) {
                flights = month;
            } else {
                flights.append(month);
            }
        }

        if (flights == null) {
            System.out.println("No BTS files found. Put the CSVs in the TFP1/Data folder.");
            return;
        }

        System.out.println(flights.shape());

        //******************************************************************************************************************************
        //***Metrics by group***

        System.out.println("\nOn-time = arrived less than 15 minutes late.");
        System.out.println("Severe delay = arrived 60+ minutes late.");
        System.out.println("Arrival stats leave out cancelled/diverted flights and missing delays.");
        System.out.println("Groups with fewer than " + MIN_FLIGHTS + " flights are not ranked.");

        Table overall = groupStats(flights, "Overall");
        overall.setName("Overall");
        System.out.println(overall);

        Table bySeason = groupStats(flights, "Season");
        bySeason.setName("By Season");
        System.out.println(bySeason);

        Table byTime = groupStats(flights, "DepartureTime");
        byTime = byTime.sortOn("+DepartureTime");
        byTime.setName("By Scheduled Departure Time");
        System.out.println(byTime);

        Table byAirline = groupStats(flights, "Airline");
        byAirline = byAirline.sortOn("-OnTimePct");
        byAirline.setName("By Operating Airline (best on-time first)");
        System.out.println(byAirline);

        Table byRoute = groupStats(flights, "Route");

        //******************************************************************************************************************************
        //***Small comparison groups***

        // Only rank routes with enough flights. Lookups for the small ones
        // should use the overall numbers instead.
        Table bigRoutes = byRoute.where(
                byRoute.doubleColumn("Flights").isGreaterThanOrEqualTo(MIN_FLIGHTS));

        int smallRoutes = byRoute.rowCount() - bigRoutes.rowCount();

        Table busiestRoutes = bigRoutes.sortOn("-Flights").first(15);
        busiestRoutes.setName("15 Busiest Routes");
        System.out.println(busiestRoutes);

        System.out.println(smallRoutes + " of " + byRoute.rowCount()
                + " routes have fewer than " + MIN_FLIGHTS
                + " flights and use the overall numbers instead.");

        //******************************************************************************************************************************
        //***Save the results for the report and the website***

        // These go in the Data folder, which is not committed to GitHub.
        bySeason.write().csv("Data/stats_by_season.csv");
        byTime.write().csv("Data/stats_by_departure_time.csv");
        byAirline.write().csv("Data/stats_by_airline.csv");
        byRoute.write().csv("Data/stats_by_route.csv");

        System.out.println("\nSaved stats_by_season, stats_by_departure_time, "
                + "stats_by_airline and stats_by_route to the Data folder.");
    }

    // Reads one BTS month and builds a smaller table with only the
    // columns we need for the comparison stats.
    public static Table readMonth(String fileName, String season) throws IOException {

        Table month = Table.read().csv(fileName);

        StringColumn airline = StringColumn.create("Airline");
        StringColumn route = StringColumn.create("Route");
        StringColumn seasonColumn = StringColumn.create("Season");
        StringColumn departureTime = StringColumn.create("DepartureTime");
        StringColumn overall = StringColumn.create("Overall");

        // AI helped me store each yes/no as 100 or 0, so the mean of the
        // column is already a percent (like a 0/1 column times 100).
        DoubleColumn cancelled = DoubleColumn.create("Cancelled");
        DoubleColumn arrived = DoubleColumn.create("Arrived");
        DoubleColumn arrivalDelay = DoubleColumn.create("ArrivalDelay");
        DoubleColumn onTime = DoubleColumn.create("OnTime");
        DoubleColumn severe = DoubleColumn.create("Severe");

        // Look up each column once instead of on every row.
        var carrierCode = month.column("OP_UNIQUE_CARRIER");
        var origin = month.column("ORIGIN");
        var dest = month.column("DEST");
        var scheduledDeparture = month.column("CRS_DEP_TIME");
        var cancelledFlag = month.numberColumn("CANCELLED");
        var divertedFlag = month.numberColumn("DIVERTED");
        var delayMinutes = month.numberColumn("ARR_DELAY");

        for (int i = 0; i < month.rowCount(); i++) {

            airline.append(carrierCode.getString(i));
            route.append(origin.getString(i) + "-" + dest.getString(i));
            seasonColumn.append(season);
            departureTime.append(timeBlock(scheduledDeparture.getString(i)));
            overall.append("All flights");

            if (cancelledFlag.getDouble(i) == 1) {
                cancelled.append(100);
            } else {
                cancelled.append(0);
            }

            // Arrival stats only use flights that landed with a delay value.
            // Missing delays are left out instead of counted as 0 minutes.
            if (cancelledFlag.getDouble(i) == 0
                    && divertedFlag.getDouble(i) == 0
                    && !delayMinutes.isMissing(i)) {

                double delay = delayMinutes.getDouble(i);

                arrived.append(1);
                arrivalDelay.append(delay);

                if (delay < 15) {
                    onTime.append(100);
                } else {
                    onTime.append(0);
                }

                if (delay >= 60) {
                    severe.append(100);
                } else {
                    severe.append(0);
                }

            } else {
                arrived.append(0);
                arrivalDelay.append(0);
                onTime.append(0);
                severe.append(0);
            }
        }

        Table result = Table.create("Flights");
        result.addColumns(airline, route, seasonColumn, departureTime, overall,
                cancelled, arrived, arrivalDelay, onTime, severe);

        return result;
    }

    // Groups the flights by one column and returns flights, on-time %,
    // average delay, severe delay % and cancellation % for each group.
    public static Table groupStats(Table flights, String groupColumn) {

        // Cancellation rate uses every flight.
        Table allFlights = flights.summarize("Cancelled",
                AggregateFunctions.count, AggregateFunctions.mean).by(groupColumn);

        // Arrival stats only use flights that actually arrived.
        Table arrivals = flights.where(flights.doubleColumn("Arrived").isEqualTo(1));

        Table arrivalStats = arrivals.summarize("OnTime", "ArrivalDelay", "Severe",
                AggregateFunctions.mean).by(groupColumn);

        // Put the two summaries side by side (same as the zip code join in CE01).
        Table stats = allFlights.joinOn(groupColumn).inner(arrivalStats);

        stats.column("Count [Cancelled]").setName("Flights");
        stats.column("Mean [OnTime]").setName("OnTimePct");
        stats.column("Mean [ArrivalDelay]").setName("AvgDelayMin");
        stats.column("Mean [Severe]").setName("SevereDelayPct");
        stats.column("Mean [Cancelled]").setName("CancelPct");

        stats = stats.selectColumns(groupColumn, "Flights", "OnTimePct",
                "AvgDelayMin", "SevereDelayPct", "CancelPct");

        stats.doubleColumn("Flights").setPrintFormatter(NumberColumnFormatter.fixedWithGrouping(0));
        stats.doubleColumn("OnTimePct").setPrintFormatter(NumberColumnFormatter.fixedWithGrouping(1));
        stats.doubleColumn("AvgDelayMin").setPrintFormatter(NumberColumnFormatter.fixedWithGrouping(1));
        stats.doubleColumn("SevereDelayPct").setPrintFormatter(NumberColumnFormatter.fixedWithGrouping(1));
        stats.doubleColumn("CancelPct").setPrintFormatter(NumberColumnFormatter.fixedWithGrouping(1));

        return stats;
    }

    // CRS_DEP_TIME is the scheduled local departure time as hhmm,
    // for example 1435 = 2:35pm. Leading numbers sort the blocks in order.
    public static String timeBlock(String time) {

        String digits = time.replace(":", "").replace(",", "").trim();

        if (digits.isEmpty()) {
            return "Unknown";
        }

        int hour = (Integer.parseInt(digits) / 100) % 24;   // 2400 means midnight

        if (hour < 6) {
            return "1 Early morning (12am-5:59am)";
        } else if (hour < 12) {
            return "2 Morning (6am-11:59am)";
        } else if (hour < 18) {
            return "3 Afternoon (12pm-5:59pm)";
        }

        return "4 Evening (6pm-11:59pm)";
    }
}