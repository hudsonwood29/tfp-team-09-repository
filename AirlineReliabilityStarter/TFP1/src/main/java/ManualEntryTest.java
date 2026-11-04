import java.util.List;

public class ManualEntryTest {

    public static void main(String[] args) {

        ManualEntrySource source = new ManualEntrySource();

        // Test 1: Nonstop flight.
        FlightSegment nonstopFlight = source.createSegment(
                "AA",
                "AA",
                "100",
                "DFW",
                "JFK",
                "2026-11-25T08:00",
                "America/Chicago",
                "2026-11-25T12:00",
                "America/New_York"
        );

        Itinerary nonstop = source.createItinerary(
                "295.50",
                "USD",
                List.of(nonstopFlight)
        );

        System.out.println("NONSTOP TEST");
        System.out.println(nonstop);

        check(
                nonstop.getTotalDurationMinutes() == 180,
                "Nonstop duration should be 180 minutes."
        );

        check(
                nonstop.isNonstop(),
                "This trip should be nonstop."
        );

        check(
                nonstop.getLayoverMinutes().isEmpty(),
                "A nonstop trip should have no layovers."
        );

        check(
                nonstop.getSource() == Itinerary.Source.MANUAL,
                "Source should be MANUAL."
        );

        // Test 2: Connecting trip crossing time zones.
        FlightSegment firstFlight = source.createSegment(
                "AA",
                "AA",
                "200",
                "SFO",
                "DFW",
                "2026-11-25T08:00",
                "America/Los_Angeles",
                "2026-11-25T13:30",
                "America/Chicago"
        );

        FlightSegment secondFlight = source.createSegment(
                "AA",
                "AA",
                "300",
                "DFW",
                "JFK",
                "2026-11-25T15:00",
                "America/Chicago",
                "2026-11-25T19:00",
                "America/New_York"
        );

        Itinerary connecting = source.createItinerary(
                "260.00",
                "USD",
                List.of(firstFlight, secondFlight)
        );

        System.out.println();
        System.out.println("CONNECTING TEST");
        System.out.println(connecting);
        System.out.println(
                "Layovers in minutes: "
                        + connecting.getLayoverMinutes()
        );

        check(
                connecting.getConnectionCount() == 1,
                "This trip should have one connection."
        );

        check(
                connecting.getLayoverMinutes().get(0) == 90,
                "The layover should be 90 minutes."
        );

        check(
                connecting.getTotalDurationMinutes() == 480,
                "Total trip duration should be 480 minutes."
        );

        // Test 3: Reject an invalid price.
        expectInvalid("Negative price", () ->
                source.createItinerary(
                        "-10.00",
                        "USD",
                        List.of(nonstopFlight)
                )
        );

        // Test 4: Reject flights that do not connect at the same airport.
        expectInvalid("Mismatched connection", () ->
                source.createItinerary(
                        "300.00",
                        "USD",
                        List.of(nonstopFlight, firstFlight)
                )
        );

        // Test 5: Reject arrival before departure.
        expectInvalid("Invalid flight times", () ->
                source.createSegment(
                        "AA",
                        "",
                        "",
                        "DFW",
                        "JFK",
                        "2026-11-25T08:00",
                        "UTC",
                        "2026-11-25T07:00",
                        "UTC"
                )
        );

        System.out.println();
        System.out.println("All manual-entry tests passed.");
        System.out.println(
                "User-entered itinerary: "
                        + "price and availability not verified."
        );
    }

    private static void check(
            boolean condition,
            String errorMessage) {

        if (!condition) {
            throw new IllegalStateException(errorMessage);
        }
    }

    // AI helped test invalid inputs as well as valid trips.
    private static void expectInvalid(
            String testName,
            Runnable action) {

        try {
            action.run();

        } catch (IllegalArgumentException e) {
            System.out.println();
            System.out.println(
                    testName + " correctly rejected: "
                            + e.getMessage()
            );
            return;
        }

        throw new IllegalStateException(
                testName + " should have been rejected."
        );
    }
}