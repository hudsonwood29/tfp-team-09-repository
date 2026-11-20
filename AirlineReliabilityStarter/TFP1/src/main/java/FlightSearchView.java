import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The home page: pick two airports by city or name, pick a date, and see
 * the Ignav flight options with the airport codes spelled out.
 */
@Route("")
@PageTitle("Compare Flights")
public class FlightSearchView extends VerticalLayout {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US);

    private final AirportDirectory airports = AirportDirectory.shared();
    private final IgnavClient ignav = new IgnavClient();

    private final ComboBox<Airport> from = airportBox("From");
    private final ComboBox<Airport> to = airportBox("To");
    private final DatePicker date = new DatePicker("Departure date");
    private final Paragraph summary = new Paragraph();
    private final Grid<Itinerary> results = new Grid<>(Itinerary.class, false);

    public FlightSearchView() {
        // Ignav needs a future date.
        date.setMin(LocalDate.now().plusDays(1));
        date.setValue(LocalDate.now().plusWeeks(2));

        Button search = new Button("Search flights", e -> search());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout form = new HorizontalLayout(from, to, date, search);
        form.setAlignItems(Alignment.BASELINE);

        setupResults();
        results.setVisible(false);

        add(new H2("Compare flights"),
                new Paragraph("Type a city, an airport name, or a three-letter code."),
                form,
                summary,
                results);
    }

    // AI helped me build a drop-down that searches the airport list,
    // so users can type "Little Rock" instead of needing to know "LIT".
    private ComboBox<Airport> airportBox(String label) {
        ComboBox<Airport> box = new ComboBox<>(label);
        box.setWidth("20em");
        box.setPlaceholder("City, airport, or code");

        // Only show airports in our BTS data, and keep the ones that match
        // what the user typed.
        box.setItems((airport, typed) -> showInList(airport, typed),
                AirportDirectory.shared().btsAirports());

        // The box shows "Little Rock, AR (LIT)".
        box.setItemLabelGenerator(Airport::shortLabel);

        // The full airport name appears under the box once one is picked.
        box.addValueChangeListener(e -> {
            if (e.getValue() == null) {
                box.setHelperText("");
            } else {
                box.setHelperText(e.getValue().name());
            }
        });

        return box;
    }

    // If the user typed an exact airport code like "LIT", show only that
    // airport. Otherwise "LIT" would also match names like "Metropolitan".
    private boolean showInList(Airport airport, String typed) {
        Airport exact = airports.find(typed);

        if (exact != null && exact.inBtsData()) {
            return airport.equals(exact);
        }

        return airport.matches(typed);
    }

    private void search() {
        Airport origin = from.getValue();
        Airport destination = to.getValue();
        LocalDate day = date.getValue();

        // Check the inputs before sending an API request.
        if (origin == null || destination == null || day == null) {
            Notification.show("Choose both airports and a departure date.");
            return;
        }
        if (origin.equals(destination)) {
            Notification.show("Departure and arrival airports must be different.");
            return;
        }
        if (!ignav.hasApiKey()) {
            Notification.show("Missing API key. Add IGNAV_API_KEY to the run configuration.");
            return;
        }

        // The page waits here until Ignav answers.
        List<Itinerary> itineraries;
        try {
            itineraries = ignav.searchOneWay(origin.code(), destination.code(), day);
        } catch (Exception e) {
            summary.setText("The search did not finish: " + e.getMessage());
            results.setVisible(false);
            return;
        }

        if (itineraries.isEmpty()) {
            summary.setText("No flights found from " + origin.fullLabel()
                    + " to " + destination.fullLabel() + ".");
            results.setVisible(false);
            return;
        }

        summary.setText(itineraries.size() + " options from " + origin.fullLabel()
                + " to " + destination.fullLabel()
                + ". Prices are from Ignav at the time of this search.");
        results.setItems(itineraries);
        results.setVisible(true);
    }

    private void setupResults() {
        results.addColumn(this::airlines)
                .setHeader("Airline");

        results.addColumn(i -> i.getCurrency() + " " + i.getPrice().toPlainString())
                .setHeader("Price")
                .setComparator(Itinerary::getPrice);

        results.addColumn(i -> i.getSegments().get(0).getDepartureTime().format(TIME))
                .setHeader("Departs (local)");

        results.addColumn(i -> lastSegment(i).getArrivalTime().format(TIME))
                .setHeader("Arrives (local)");

        // Every airport on the trip, spelled out, e.g.
        // "Little Rock, AR (LIT) -> Dallas-Fort Worth, TX (DFW) -> New York, NY (JFK)"
        results.addColumn(this::route)
                .setHeader("Route")
                .setFlexGrow(3);

        results.addColumn(i -> hoursAndMinutes(i.getTotalDurationMinutes()))
                .setHeader("Total time")
                .setComparator(Itinerary::getTotalDurationMinutes);

        results.addColumn(this::stops)
                .setHeader("Stops")
                .setComparator(Itinerary::getConnectionCount);
    }

    private String route(Itinerary itinerary) {
        List<String> places = new ArrayList<>();
        places.add(airports.shortName(itinerary.getDepartureAirport()));

        for (FlightSegment segment : itinerary.getSegments()) {
            places.add(airports.shortName(segment.getArrivalAirport()));
        }

        return String.join(" -> ", places);
    }

    private String stops(Itinerary itinerary) {
        if (itinerary.isNonstop()) {
            return "Nonstop";
        }

        // e.g. "1 stop, 1h 30m layover"
        List<String> layovers = new ArrayList<>();
        for (long minutes : itinerary.getLayoverMinutes()) {
            layovers.add(hoursAndMinutes(minutes));
        }

        int count = itinerary.getConnectionCount();
        String word = count == 1 ? " stop" : " stops";
        return count + word + ", " + String.join(" + ", layovers) + " layover";
    }

    private String airlines(Itinerary itinerary) {
        List<String> codes = new ArrayList<>();
        for (FlightSegment segment : itinerary.getSegments()) {
            if (!codes.contains(segment.getMarketingAirlineCode())) {
                codes.add(segment.getMarketingAirlineCode());
            }
        }
        return String.join(", ", codes);
    }

    private static FlightSegment lastSegment(Itinerary itinerary) {
        List<FlightSegment> segments = itinerary.getSegments();
        return segments.get(segments.size() - 1);
    }

    private static String hoursAndMinutes(long minutes) {
        return (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m";
    }
}
