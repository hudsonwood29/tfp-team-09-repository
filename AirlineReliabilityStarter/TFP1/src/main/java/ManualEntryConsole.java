import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ManualEntryConsole {

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        ManualEntrySource source = new ManualEntrySource();
        List<FlightSegment> segments = new ArrayList<>();

        try {
            String price = ask(input, "Ticket price, e.g. 295.50: ");
            String currency = ask(input, "Currency, e.g. USD: ");

            do {
                System.out.println("\nFlight segment " + (segments.size() + 1));

                String marketing = ask(input, "Airline code on ticket, e.g. AA: ");
                String operating = ask(input, "Operating airline code (optional): ");
                String number = ask(input, "Flight number (optional): ");
                String from = ask(input, "Departure airport, e.g. DFW: ");
                String to = ask(input, "Arrival airport, e.g. JFK: ");
                String departure = ask(input, "Departure local date/time (YYYY-MM-DDTHH:MM): ");
                String departureZone = ask(input, "Departure time zone, e.g. America/Chicago: ");
                String arrival = ask(input, "Arrival local date/time (YYYY-MM-DDTHH:MM): ");
                String arrivalZone = ask(input, "Arrival time zone, e.g. America/New_York: ");

                // AI helped me connect console input to the existing itinerary validation.
                segments.add(source.createSegment(
                        marketing, operating, number, from, to,
                        departure, departureZone, arrival, arrivalZone
                ));
            } while (ask(input, "Add a connecting flight? (y/n): ").equalsIgnoreCase("y"));

            Itinerary trip = source.createItinerary(price, currency, segments);
            System.out.println("\n" + trip);
            System.out.println("Layovers in minutes: " + trip.getLayoverMinutes());
            System.out.println("User-entered itinerary: price and availability not verified.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not create itinerary: " + e.getMessage());
        }
    }

    private static String ask(Scanner input, String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) {
            throw new IllegalArgumentException("No console input received.");
        }
        return input.nextLine().trim();
    }
}