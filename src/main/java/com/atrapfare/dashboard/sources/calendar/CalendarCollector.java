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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import com.atrapfare.dashboard.core.CollectorRegistry;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.util.CompatibilityHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import org.springframework.web.client.RestClient;

@Component
public class CalendarCollector implements Collector<CalendarData> {

	private static final Logger log = LoggerFactory.getLogger(CalendarCollector.class);

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
	public CalendarData collect() {
		List<DashboardProperties.Calendar.Source> sources = sources();
		if (sources.isEmpty()) {
			throw new IllegalStateException("Keine Kalenderquelle eingetragen");
		}

		ZonedDateTime now = ZonedDateTime.now(zone);
		List<SourceResult> results;
		// Die Kalender werden nebeneinander geholt. Nacheinander summierten sich
		// ihre Wartezeiten, und schon einer allein kann auf einer schlechten
		// Leitung mehrere Sekunden brauchen.
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<CompletableFuture<SourceResult>> futures = sources.stream()
					.map(source -> CompletableFuture.supplyAsync(() -> fetch(source, now), executor))
					.toList();
			results = futures.stream().map(CompletableFuture::join).toList();
		}

		// Ein ausgefallener Kalender darf die uebrigen nicht entwerten - nur wenn
		// keiner durchkam, gibt es nichts anzuzeigen.
		List<CalendarData.SourceFailure> failures = results.stream()
				.filter(result -> result.error() != null)
				.map(result -> new CalendarData.SourceFailure(result.name(), result.error()))
				.toList();

		if (failures.size() == results.size()) {
			throw new IllegalStateException("Kein Kalender erreichbar: " + failures.stream()
					.map(failure -> failure.name() + " (" + failure.error() + ")")
					.toList());
		}

		List<CalendarEvent> events = new ArrayList<>();
		results.forEach(result -> events.addAll(result.events()));
		events.sort(Comparator.comparing(CalendarEvent::start).thenComparing(CalendarEvent::title));

		long contributing = results.stream().filter(result -> result.error() == null).count();
		return new CalendarData(List.copyOf(events), failures, (int) contributing);
	}

	/**
	 * Die einzelne Adresse aus der bisherigen Einstellung zaehlt als weitere
	 * Quelle - bestehende Installationen laufen damit unveraendert weiter.
	 */
	private List<DashboardProperties.Calendar.Source> sources() {
		List<DashboardProperties.Calendar.Source> all = new ArrayList<>();
		String single = properties.icalUrl();
		if (single != null && !single.isBlank()) {
			all.add(new DashboardProperties.Calendar.Source("Privat", single));
		}
		if (properties.sources() != null) {
			properties.sources().stream()
					.filter(source -> source.url() != null && !source.url().isBlank())
					.forEach(all::add);
		}
		return all;
	}

	private SourceResult fetch(DashboardProperties.Calendar.Source source, ZonedDateTime now) {
		String name = (source.name() == null || source.name().isBlank()) ? "Kalender" : source.name();
		try {
			byte[] body = download(source.url());
			if (body == null || body.length == 0) {
				return new SourceResult(name, List.of(), "leere Antwort");
			}
			return new SourceResult(name, parse(body, now, name), null);
		}
		catch (Exception e) {
			log.warn("Kalender '{}' fehlgeschlagen: {}", name, e.toString());
			String message = (e.getMessage() == null || e.getMessage().isBlank())
					? e.getClass().getSimpleName() : CollectorRegistry.shorten(e.getMessage());
			return new SourceResult(name, List.of(), message);
		}
	}

	/**
	 * iCal-Dateien sind Text und schrumpfen stark: der Google-Kalender liefert
	 * rund 90 statt 464 kB. Auf einer verlustbehafteten Leitung entscheidet die
	 * Menge der Bytes darueber, ob der Abruf durchkommt. Der JDK-Client packt
	 * nicht von selbst aus, das erledigt {@link #unzip}.
	 */
	private byte[] download(String url) throws Exception {
		var response = restClient.get()
				.uri(URI.create(url))
				.header(HttpHeaders.ACCEPT_ENCODING, "gzip")
				.retrieve()
				.toEntity(byte[].class);

		byte[] body = response.getBody();
		boolean gzipped = response.getHeaders()
				.getOrEmpty(HttpHeaders.CONTENT_ENCODING).stream()
				.anyMatch(value -> value.toLowerCase().contains("gzip"));
		return gzipped ? unzip(body) : body;
	}

	private static byte[] unzip(byte[] body) throws Exception {
		if (body == null || body.length == 0) {
			return body;
		}
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body));
				ByteArrayOutputStream out = new ByteArrayOutputStream(body.length * 4)) {
			in.transferTo(out);
			return out.toByteArray();
		}
	}

	private record SourceResult(String name, List<CalendarEvent> events, String error) {
	}

	List<CalendarEvent> parse(byte[] ical, ZonedDateTime now, String source) throws Exception {
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
						state(start, end, allDay, now),
						source));
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
