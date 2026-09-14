package com.atrapfare.dashboard.sources.transit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Holt die naechsten Verbindungen beim VVS ueber die EFA-Schnittstelle.
 *
 * Bewusst die Verbindungsauskunft und nicht der Abfahrtsmonitor: an einer
 * Haltestelle faehrt dieselbe Linie in beide Richtungen, und ob eine Abfahrt
 * ans Ziel fuehrt, weiss erst die Auskunft. Sie traegt ausserdem einen
 * spaeteren Umstieg mit, falls sich der Fahrplan einmal aendert.
 */
@Component
public class TransitCollector implements Collector<TransitData> {

	/**
	 * Ab dieser Verkehrsmittelklasse handelt es sich um Fusswege statt um
	 * Fahrten (99 = "Fussweg", 100 = "footpath").
	 */
	private static final int WALK_FROM_CLASS = 99;

	/**
	 * Die Schnittstelle liefert der Reihe nach und beginnt dabei gelegentlich
	 * vor der aktuellen Zeit. Ein kleiner Zuschlag sorgt dafuer, dass nach dem
	 * Aussortieren noch genug uebrig bleibt.
	 */
	private static final int EXTRA_RESULTS = 4;

	/**
	 * Eine Verbindung, die gerade abfaehrt, ist noch zu erreichen; eine, die
	 * vor zwei Minuten weg war, nicht mehr.
	 */
	private static final Duration GRACE = Duration.ofMinutes(1);

	private final DashboardProperties.Transit properties;

	private final RestClient restClient;

	public TransitCollector(DashboardProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties.transit();
		this.restClient = restClientBuilder.build();
	}

	@Override
	public String id() {
		return "transit";
	}

	@Override
	public Duration interval() {
		return properties.interval();
	}

	@Override
	public TransitData collect() {
		EfaTripResponse response = restClient.get()
				.uri(properties.baseUrl() + "/XML_TRIP_REQUEST2", uri -> uri
						.queryParam("outputFormat", "rapidJSON")
						.queryParam("type_origin", "any")
						.queryParam("name_origin", properties.origin())
						.queryParam("type_destination", "any")
						.queryParam("name_destination", properties.destination())
						.queryParam("itdTripDateTimeDepArr", "dep")
						.queryParam("calcNumberOfTrips", properties.results() + EXTRA_RESULTS)
						.queryParam("useRealtime", 1)
						.queryParam("language", "de")
						.build())
				.retrieve()
				.body(EfaTripResponse.class);

		return map(response, Instant.now());
	}

	TransitData map(EfaTripResponse response, Instant now) {
		if (response == null || response.journeys() == null) {
			throw new IllegalStateException("VVS lieferte keine verwertbare Antwort");
		}

		Instant earliest = now.minus(GRACE);
		List<TransitData.Connection> connections = new ArrayList<>();
		for (EfaTripResponse.Journey journey : response.journeys()) {
			TransitData.Connection connection = toConnection(journey);
			if (connection != null && connection.departure().isAfter(earliest)) {
				connections.add(connection);
			}
			if (connections.size() == properties.results()) {
				break;
			}
		}

		return new TransitData(originName(response), destinationName(response), List.copyOf(connections));
	}

	private TransitData.Connection toConnection(EfaTripResponse.Journey journey) {
		List<EfaTripResponse.Leg> legs = (journey.legs() == null) ? List.of() : journey.legs();
		if (legs.isEmpty()) {
			return null;
		}

		EfaTripResponse.Point start = legs.getFirst().origin();
		EfaTripResponse.Point end = legs.getLast().destination();
		if (start == null || end == null) {
			return null;
		}

		Instant planned = start.departureTimePlanned();
		Instant estimated = start.departureTimeEstimated();
		Instant departure = (estimated != null) ? estimated : planned;
		Instant arrival = (end.arrivalTimeEstimated() != null) ? end.arrivalTimeEstimated() : end.arrivalTimePlanned();
		if (departure == null || arrival == null) {
			return null;
		}

		int delay = (estimated == null || planned == null)
				? 0 : (int) Duration.between(planned, estimated).toMinutes();

		return new TransitData.Connection(
				departure,
				(planned != null) ? planned : departure,
				delay,
				estimated != null,
				arrival,
				(int) Duration.between(departure, arrival).toMinutes(),
				(journey.interchanges() == null) ? 0 : journey.interchanges(),
				legs.stream().map(TransitCollector::toLeg).toList());
	}

	private static TransitData.Leg toLeg(EfaTripResponse.Leg leg) {
		EfaTripResponse.Transportation transport = leg.transportation();
		EfaTripResponse.Transportation.Product product = (transport == null) ? null : transport.product();
		boolean walk = product == null || product.productClass() == null
				|| product.productClass() >= WALK_FROM_CLASS;

		EfaTripResponse.Point from = leg.origin();
		EfaTripResponse.Point to = leg.destination();

		return new TransitData.Leg(
				walk ? "" : line(transport),
				walk ? "Fußweg" : text(product == null ? null : product.name()),
				(walk || transport.destination() == null) ? "" : text(transport.destination().name()),
				(from == null) ? "" : text(from.name()),
				(to == null) ? "" : text(to.name()),
				platform(from),
				(from == null) ? null : firstOf(from.departureTimeEstimated(), from.departureTimePlanned()),
				(to == null) ? null : firstOf(to.arrivalTimeEstimated(), to.arrivalTimePlanned()),
				(leg.duration() == null) ? 0 : Math.round(leg.duration() / 60f),
				walk);
	}

	/**
	 * {@code disassembledName} ist die kurze Form ("91", "S2"), die aufs Schild
	 * gehoert. {@code number} traegt je nach Linie den ausgeschriebenen Namen.
	 */
	private static String line(EfaTripResponse.Transportation transport) {
		if (transport.disassembledName() != null && !transport.disassembledName().isBlank()) {
			return transport.disassembledName();
		}
		return text(transport.number());
	}

	private static String platform(EfaTripResponse.Point point) {
		if (point == null || point.properties() == null) {
			return "";
		}
		return text(point.properties().platform());
	}

	private static Instant firstOf(Instant preferred, Instant fallback) {
		return (preferred != null) ? preferred : fallback;
	}

	private static String text(String value) {
		return (value == null) ? "" : value;
	}

	/**
	 * Die Namen stammen aus der Antwort statt aus der Konfiguration: so steht
	 * ueber der Kachel, wie der VVS die Haltestelle nennt, und nicht, wie sie
	 * einmal in die Einstellungen getippt wurde.
	 */
	private String originName(EfaTripResponse response) {
		return endpointName(response, true);
	}

	private String destinationName(EfaTripResponse response) {
		return endpointName(response, false);
	}

	private String endpointName(EfaTripResponse response, boolean start) {
		for (EfaTripResponse.Journey journey : response.journeys()) {
			if (journey.legs() == null || journey.legs().isEmpty()) {
				continue;
			}
			EfaTripResponse.Point point = start
					? journey.legs().getFirst().origin()
					: journey.legs().getLast().destination();
			if (point != null && point.name() != null && !point.name().isBlank()) {
				return point.name();
			}
		}
		return start ? properties.origin() : properties.destination();
	}
}
