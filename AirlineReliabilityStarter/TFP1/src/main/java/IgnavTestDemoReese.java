import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class IgnavTestDemoReese {

    public static void main(String[] args) throws Exception {

        // Read your key from IntelliJ's run configuration.
        String apiKey = System.getenv("IGNAV_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println(
                    "Missing API key. Add IGNAV_API_KEY to your run configuration."
            );
            return;
        }

        // Test search: Chicago to New York, 30 days from today.
        Scanner scanner = new Scanner(System.in);

        System.out.print("Departure airport code (example: ORD): ");
        String origin = scanner.nextLine().trim().toUpperCase();

        System.out.print("Arrival airport code (example: JFK): ");
        String destination = scanner.nextLine().trim().toUpperCase();

        System.out.print("Departure date (YYYY-MM-DD): ");
        String departureDate = scanner.nextLine().trim();

        // Check the inputs before sending an API request.
        if (!origin.matches("[A-Z]{3}") ||
                !destination.matches("[A-Z]{3}")) {
            System.out.println("Use three-letter airport codes, such as ORD and JFK.");
            return;
        }

        try {
            LocalDate date = LocalDate.parse(departureDate);

            if (!date.isAfter(LocalDate.now())) {
                System.out.println("Enter a future departure date.");
                return;
            }
        } catch (java.time.format.DateTimeParseException e) {
            System.out.println("Enter the date in YYYY-MM-DD format.");
            return;
        }

        String requestBody = String.format(
                "{\"origin\":\"%s\",\"destination\":\"%s\","
                        + "\"departure_date\":\"%s\"}",
                origin,
                destination,
                departureDate
        );

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ignav.com/api/fares/one-way"))
                .timeout(Duration.ofSeconds(120))
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        System.out.println("Searching " + origin + " to " + destination);
        System.out.println("Departure date: " + departureDate);

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            System.out.println("HTTP status: " + response.statusCode());
            System.out.println(response.body());
            return;
        }

        // Create the list, then fill it with Itinerary objects
        // (same pattern as KPSolver's readProblemData).
        ArrayList<Itinerary> itineraries = new ArrayList<>();
        IgnavMapper.readIgnavData(response.body(), itineraries);

        if (itineraries.isEmpty()) {
            System.out.println("No flight options found.");
            return;
        }

        System.out.println("\nFlight options found: " + itineraries.size());

        System.out.printf(
                "%-10s %10s %-5s %-17s %-17s %8s %6s %-10s%n",
                "Airline", "Price", "Curr", "Depart (local)",
                "Arrive (local)", "Minutes", "Stops", "Layovers"
        );

        DateTimeFormatter clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Itinerary itinerary : itineraries) {
            List<FlightSegment> segments = itinerary.getSegments();
            FlightSegment first = segments.get(0);
            FlightSegment last = segments.get(segments.size() - 1);

            System.out.printf(
                    "%-10s %10s %-5s %-17s %-17s %8d %6d %-10s%n",
                    first.getMarketingAirlineCode(),
                    itinerary.getPrice().toPlainString(),
                    itinerary.getCurrency(),
                    first.getDepartureTime().format(clock),
                    last.getArrivalTime().format(clock),
                    itinerary.getTotalDurationMinutes(),
                    itinerary.getConnectionCount(),
                    itinerary.getLayoverMinutes()
            );

            // Show each leg so connections are easy to check.
            for (FlightSegment segment : segments) {
                System.out.println("    " + segment);
            }
        }
    }
}