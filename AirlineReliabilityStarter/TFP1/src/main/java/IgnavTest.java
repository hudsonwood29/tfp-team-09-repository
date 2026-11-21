import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Scanner;

public class IgnavTest {

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

        // Show one itinerary's actual fields so we can check the mapping.
        JsonObject debugRoot =
                JsonParser.parseString(response.body()).getAsJsonObject();

        JsonArray debugTrips = debugRoot.getAsJsonArray("itineraries");

        if (debugTrips != null && !debugTrips.isEmpty()) {
            System.out.println("\nFIRST ITINERARY JSON:");
            System.out.println(
                    new com.google.gson.GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(debugTrips.get(0))
            );
        }

        // Convert the response text into JSON we can read.
        JsonObject root = JsonParser.parseString(
                response.body()
        ).getAsJsonObject();

        JsonArray itineraries = root.getAsJsonArray("itineraries");

        if (itineraries == null || itineraries.isEmpty()) {
            System.out.println("No flight options found.");
            return;
        }

        System.out.println("\nFlight options found: " + itineraries.size());

        System.out.printf(
                "%-25s %10s %-5s %-19s %-19s %8s %6s%n",
                "Airline", "Price", "Curr", "Depart (local)",
                "Arrive (local)", "Minutes", "Stops"
        );

        for (JsonElement element : itineraries) {
            JsonObject itinerary = element.getAsJsonObject();

            JsonObject price = itinerary.getAsJsonObject("price");
            JsonObject outbound = itinerary.getAsJsonObject("outbound");
            JsonArray segments = outbound.getAsJsonArray("segments");

            if (segments == null || segments.isEmpty()) {
                continue;
            }

            // The first segment starts the trip.
            JsonObject firstSegment = segments.get(0).getAsJsonObject();

            // The last segment ends the trip.
            JsonObject lastSegment = segments.get(
                    segments.size() - 1
            ).getAsJsonObject();

            String airline = outbound.get("carrier").getAsString();
            double amount = price.get("amount").getAsDouble();
            String currency = price.get("currency").getAsString();

            String departure = firstSegment
                    .get("departure_time_local").getAsString();

            String arrival = lastSegment
                    .get("arrival_time_local").getAsString();

            int minutes = outbound.get("duration_minutes").getAsInt();
            int stops = segments.size() - 1;

            System.out.printf(
                    "%-25s %10.2f %-5s %-19s %-19s %8d %6d%n",
                    airline, amount, currency,
                    departure, arrival, minutes, stops
            );
        }
    }
}

