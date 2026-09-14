package com.atrapfare.dashboard.sources.calendar;

import java.time.Instant;

public record CalendarEvent(
		String title,
		Instant start,
		Instant end,
		boolean allDay,
		String location,
		EventState state,
		/** Name des Kalenders, aus dem der Termin stammt. Leer bei nur einer Quelle. */
		String source
) {
}
