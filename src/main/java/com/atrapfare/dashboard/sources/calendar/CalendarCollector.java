package com.atrapfare.dashboard.sources.calendar;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.util.CompatibilityHints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.URI;
import org.springframework.web.client.RestClient;

@Component
public class CalendarCollector implements Collector<List<CalendarEvent>> {

	/**
	 * Das Abfragefenster beginnt bewusst vor "jetzt". Ein mehrtaegiger oder
	 * gerade laufender Termin hat seinen Start in der Vergangenheit und faellt
	 * sonst aus der Wiederholungsberechnung heraus, obwohl er noch laeuft.
	 */
	private static final Duration LOOK_BACK = Duration.ofDays(7);

	static {
		// Oeffentliche iCal-Feeds halten sich haeufig nicht streng an RFC 5545.
		// Lieber tolerant parsen als das ganze Widget an einer Kleinigkeit
		// scheitern lassen.
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true);
	}

	private final DashboardProperties.Calendar properties;

	private final RestClient restClient;

	private final ZoneId zone;

	@Autowired
	public CalendarCollector(DashboardProperties properties, RestClient.Builder restClientBuilder) {
		this(properties, restClientBuilder, ZoneId.systemDefault());
	}

	// Die Zeitzone ist im Betrieb die des Servers, im Test fest vorgegeben.
	CalendarCollector(DashboardProperties properties, RestClient.Builder restClientBuilder, ZoneId zone) {
		this.properties = properties.calendar();
		this.restClient = restClientBuilder.build();
		this.zone = zone;
	}

	@Override
	public String id() {
		return "calendar";
	}

	@Override
	public Duration interval() {
		return properties.interval();
	}

	@Override
	public List<CalendarEvent> collect() throws Exception {
		String url = properties.icalUrl();
		if (url == null || url.isBlank()) {
			throw new IllegalStateException("CALENDAR_ICAL_URL ist nicht gesetzt");
		}

		byte[] body = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
		if (body == null || body.length == 0) {
			throw new IllegalStateException("iCal-Quelle lieferte keinen Inhalt");
		}

		return parse(body, ZonedDateTime.now(zone));
	}

	List<CalendarEvent> parse(byte[] ical, ZonedDateTime now) throws Exception {
		net.fortuna.ical4j.model.Calendar calendar =
				new CalendarBuilder().build(new ByteArrayInputStream(ical));

		ZonedDateTime windowStart = now.minus(LOOK_BACK);
		ZonedDateTime windowEnd = now.plus(properties.lookAhead());
		Instant from = now.toInstant();
		Instant until = windowEnd.toInstant();

		List<CalendarEvent> events = new ArrayList<>();
		for (VEvent event : calendar.<VEvent>getComponents(net.fortuna.ical4j.model.Component.VEVENT)) {
			// calculateRecurrenceSet expandiert RRULE, RDATE und EXDATE und
			// liefert auch fuer Einzeltermine genau eine Periode zurueck.
			Set<Period<Temporal>> occurrences =
					event.calculateRecurrenceSet(window(event, windowStart, windowEnd));
			for (Period<Temporal> occurrence : occurrences) {
				boolean allDay = occurrence.getStart() instanceof LocalDate;
				Instant start = toInstant(occurrence.getStart());
				Instant end = toInstant(occurrence.getEnd());

				// Bereits beendete Termine entfallen, laufende bleiben.
				if (end == null || !end.isAfter(from) || start == null || !start.isBefore(until)) {
					continue;
				}

				events.add(new CalendarEvent(
						title(event),
						start,
						end,
						allDay,
						location(event),
						state(start, end, allDay, now)));
			}
		}

		events.sort(Comparator.comparing(CalendarEvent::start).thenComparing(CalendarEvent::title));
		return List.copyOf(events);
	}

	/**
	 * ical4j vergleicht die Grenzen des Abfragefensters direkt mit den
	 * Terminzeiten. Ein ganztaegiger Termin ist ein LocalDate, ein Termin mit
	 * Uhrzeit ein ZonedDateTime oder Instant - mischt man beides, scheitert der
	 * Vergleich an der fehlenden Zeiteinheit. Das Fenster bekommt darum je
	 * Termin denselben Temporal-Typ wie dessen DTSTART.
	 */
	private Period<? extends Temporal> window(VEvent event, ZonedDateTime start, ZonedDateTime end) {
		Temporal reference = event.<Temporal>getStartDate().map(DtStart::getDate).orElse(null);
		return switch (reference) {
			case LocalDate ignored -> new Period<>(start.toLocalDate(), end.toLocalDate().plusDays(1));
			case LocalDateTime ignored -> new Period<>(start.toLocalDateTime(), end.toLocalDateTime());
			case null, default -> new Period<>(start, end);
		};
	}

	private EventState state(Instant start, Instant end, boolean allDay, ZonedDateTime now) {
		Instant reference = now.toInstant();
		// Ein ganztaegiger Termin laeuft zwar technisch gerade, gemeint ist aber
		// "heute" - "laufend" bleibt den Terminen mit Uhrzeit vorbehalten.
		if (!allDay && !start.isAfter(reference) && end.isAfter(reference)) {
			return EventState.LAUFEND;
		}
		LocalDate startDay = start.atZone(zone).toLocalDate();
		LocalDate today = now.toLocalDate();
		if (startDay.equals(today)) {
			return EventState.HEUTE;
		}
		if (startDay.equals(today.plusDays(1))) {
			return EventState.MORGEN;
		}
		return EventState.SPAETER;
	}

	/**
	 * iCal kennt drei Zeitformen: UTC, zonengebunden ueber VTIMEZONE und reine
	 * Datumsangaben fuer ganztaegige Termine. Ganztaegige Termine bekommen die
	 * lokale Mitternacht, damit sie am richtigen Kalendertag erscheinen.
	 */
	private Instant toInstant(Temporal temporal) {
		return switch (temporal) {
			case null -> null;
			case Instant instant -> instant;
			case ZonedDateTime zonedDateTime -> zonedDateTime.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			case LocalDateTime localDateTime -> localDateTime.atZone(zone).toInstant();
			case LocalDate localDate -> localDate.atStartOfDay(zone).toInstant();
			default -> Instant.from(temporal);
		};
	}

	private static String title(VEvent event) {
		Summary summary = event.getSummary();
		if (summary == null || summary.getValue() == null || summary.getValue().isBlank()) {
			return "(ohne Titel)";
		}
		return summary.getValue();
	}

	private static String location(VEvent event) {
		Location location = event.getLocation();
		if (location == null || location.getValue() == null || location.getValue().isBlank()) {
			return null;
		}
		return location.getValue();
	}
}
