package com.atrapfare.dashboard.sources.calendar;

import java.util.List;

/**
 * Termine aus allen eingetragenen Kalendern, zusammengefuehrt und nach Beginn
 * sortiert.
 *
 * @param events         Termine im Abfragefenster
 * @param failedSources  Kalender, die diesmal nicht erreichbar waren. Ein
 *                       Ausfall entwertet die uebrigen nicht.
 * @param sourceCount    Anzahl der Kalender, die Termine beigesteuert haben.
 *                       Erst ab zwei lohnt es sich, die Herkunft anzuzeigen.
 */
public record CalendarData(
		List<CalendarEvent> events,
		List<SourceFailure> failedSources,
		int sourceCount
) {

	public record SourceFailure(String name, String error) {
	}
}
