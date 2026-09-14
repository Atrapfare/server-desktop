package com.atrapfare.dashboard.sources.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.atrapfare.dashboard.config.DashboardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarCollectorTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

	// Montag, 14.09.2026, 09:00 Ortszeit.
	private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 9, 14, 9, 0, 0, 0, BERLIN);

	private static final String ICAL = """
			BEGIN:VCALENDAR
			VERSION:2.0
			PRODID:-//Test//DE
			BEGIN:VTIMEZONE
			TZID:Europe/Berlin
			BEGIN:STANDARD
			DTSTART:19701025T030000
			TZOFFSETFROM:+0200
			TZOFFSETTO:+0100
			RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
			TZNAME:CET
			END:STANDARD
			BEGIN:DAYLIGHT
			DTSTART:19700329T020000
			TZOFFSETFROM:+0100
			TZOFFSETTO:+0200
			RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
			TZNAME:CEST
			END:DAYLIGHT
			END:VTIMEZONE
			BEGIN:VEVENT
			UID:woechentlich@test
			DTSTART;TZID=Europe/Berlin:20260907T140000
			DTEND;TZID=Europe/Berlin:20260907T150000
			RRULE:FREQ=WEEKLY;BYDAY=MO
			SUMMARY:Wochenrunde
			LOCATION:Besprechungsraum
			END:VEVENT
			BEGIN:VEVENT
			UID:ganztaegig@test
			DTSTART;VALUE=DATE:20260914
			DTEND;VALUE=DATE:20260915
			SUMMARY:Betriebsausflug
			END:VEVENT
			BEGIN:VEVENT
			UID:utc@test
			DTSTART:20260914T180000Z
			DTEND:20260914T190000Z
			SUMMARY:Abendtermin UTC
			END:VEVENT
			BEGIN:VEVENT
			UID:laufend@test
			DTSTART;TZID=Europe/Berlin:20260914T083000
			DTEND;TZID=Europe/Berlin:20260914T100000
			SUMMARY:Laeuft gerade
			END:VEVENT
			BEGIN:VEVENT
			UID:vorbei@test
			DTSTART;TZID=Europe/Berlin:20260914T070000
			DTEND;TZID=Europe/Berlin:20260914T073000
			SUMMARY:Schon vorbei
			END:VEVENT
			BEGIN:VEVENT
			UID:mitAusnahme@test
			DTSTART;TZID=Europe/Berlin:20260907T110000
			DTEND;TZID=Europe/Berlin:20260907T113000
			RRULE:FREQ=DAILY
			EXDATE;TZID=Europe/Berlin:20260914T110000
			SUMMARY:Taeglich ausser heute
			END:VEVENT
			END:VCALENDAR
			""";

	private List<CalendarEvent> parse(Duration lookAhead) throws Exception {
		DashboardProperties properties = new DashboardProperties(
				null,
				new DashboardProperties.Calendar("http://unbenutzt", Duration.ofMinutes(15), lookAhead),
				null,
				null,
				null,
				null);
		CalendarCollector collector = new CalendarCollector(properties, RestClient.builder(), BERLIN);
		return collector.parse(ICAL.getBytes(StandardCharsets.UTF_8), NOW);
	}

	@Test
	void serienterminWirdAufDenRichtigenTagExpandiert() throws Exception {
		List<CalendarEvent> events = parse(Duration.ofHours(24));

		CalendarEvent wochenrunde = events.stream()
				.filter(event -> event.title().equals("Wochenrunde"))
				.findFirst()
				.orElseThrow();

		assertThat(wochenrunde.start()).isEqualTo(ZonedDateTime.of(2026, 9, 14, 14, 0, 0, 0, BERLIN).toInstant());
		assertThat(wochenrunde.end()).isEqualTo(ZonedDateTime.of(2026, 9, 14, 15, 0, 0, 0, BERLIN).toInstant());
		assertThat(wochenrunde.allDay()).isFalse();
		assertThat(wochenrunde.location()).isEqualTo("Besprechungsraum");
		assertThat(wochenrunde.state()).isEqualTo(EventState.HEUTE);
	}

	@Test
	void serienterminErscheintImNaechstenFensterErneut() throws Exception {
		List<CalendarEvent> events = parse(Duration.ofDays(8));

		assertThat(events)
				.filteredOn(event -> event.title().equals("Wochenrunde"))
				.extracting(CalendarEvent::start)
				.containsExactly(
						ZonedDateTime.of(2026, 9, 14, 14, 0, 0, 0, BERLIN).toInstant(),
						ZonedDateTime.of(2026, 9, 21, 14, 0, 0, 0, BERLIN).toInstant());
	}

	@Test
	void ganztaegigerTerminBeginntZurLokalenMitternacht() throws Exception {
		CalendarEvent ausflug = parse(Duration.ofHours(24)).stream()
				.filter(event -> event.title().equals("Betriebsausflug"))
				.findFirst()
				.orElseThrow();

		assertThat(ausflug.allDay()).isTrue();
		assertThat(ausflug.start()).isEqualTo(ZonedDateTime.of(2026, 9, 14, 0, 0, 0, 0, BERLIN).toInstant());
		assertThat(ausflug.state()).isEqualTo(EventState.HEUTE);
	}

	@Test
	void utcTerminWirdKorrektUmgerechnet() throws Exception {
		CalendarEvent abend = parse(Duration.ofHours(24)).stream()
				.filter(event -> event.title().equals("Abendtermin UTC"))
				.findFirst()
				.orElseThrow();

		assertThat(abend.start()).isEqualTo(ZonedDateTime.of(2026, 9, 14, 20, 0, 0, 0, BERLIN).toInstant());
		assertThat(abend.state()).isEqualTo(EventState.HEUTE);
	}

	@Test
	void laufenderTerminBleibtSichtbarUndIstAlsLaufendMarkiert() throws Exception {
		CalendarEvent laufend = parse(Duration.ofHours(24)).stream()
				.filter(event -> event.title().equals("Laeuft gerade"))
				.findFirst()
				.orElseThrow();

		assertThat(laufend.state()).isEqualTo(EventState.LAUFEND);
	}

	@Test
	void beendeterTerminFaelltHeraus() throws Exception {
		assertThat(parse(Duration.ofHours(24)))
				.extracting(CalendarEvent::title)
				.doesNotContain("Schon vorbei");
	}

	@Test
	void exdateUnterdruecktDieAusgenommeneWiederholung() throws Exception {
		assertThat(parse(Duration.ofHours(24)))
				.extracting(CalendarEvent::title)
				.doesNotContain("Taeglich ausser heute");
	}

	@Test
	void ergebnisIstNachStartzeitSortiert() throws Exception {
		List<CalendarEvent> events = parse(Duration.ofHours(24));

		assertThat(events).isSortedAccordingTo(java.util.Comparator.comparing(CalendarEvent::start));
		assertThat(events).extracting(CalendarEvent::title)
				.startsWith("Betriebsausflug", "Laeuft gerade", "Wochenrunde", "Abendtermin UTC");
	}
}
