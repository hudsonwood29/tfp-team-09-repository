import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * The Ignav request from IgnavTestDemoReese, moved into its own class so the
 * website can call it. Reads the key from IGNAV_API_KEY, the same as the demo.
 */
public class IgnavClient {

    private final String apiKey = System.getenv("IGNAV_API_KEY");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // AI helped me turn the console demo into a method that takes the
    // airports and date as parameters instead of reading them from Scanner.
    public ArrayList<Itinerary> searchOneWay(
            String origin, String destination, LocalDate departureDate)
            throws IOException, InterruptedException {

        String requestBody = String.format(
                "{\"origin\":\"%s\",\"destination\":\"%s\","
                        + "\"departure_date\":\"%s\"}",
                origin,
                destination,
                departureDate
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ignav.com/api/fares/one-way"))
                .timeout(Duration.ofSeconds(120))
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException("Ignav returned HTTP " + response.statusCode());
        }

        // Same pattern as the demo: create the list, then fill it.
        ArrayList<Itinerary> itineraries = new ArrayList<>();
        IgnavMapper.readIgnavData(response.body(), itineraries);
        return itineraries;
    }
}
