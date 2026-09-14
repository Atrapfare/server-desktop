package com.atrapfare.dashboard.sources.transit;

import java.time.Instant;
import java.util.List;

/**
 * Die naechsten Verbindungen von einer Haltestelle zur anderen.
 *
 * @param origin      Name der Starthaltestelle, wie ihn die Auskunft nennt
 * @param destination Name der Zielhaltestelle
 * @param connections aufsteigend nach Abfahrt, bereits abgefahrene sind raus
 */
public record TransitData(
		String origin,
		String destination,
		List<Connection> connections
) {

	/**
	 * @param departure     tatsaechliche Abfahrt: Echtzeit, wenn vorhanden, sonst Plan
	 * @param planned       planmaessige Abfahrt
	 * @param delayMinutes  Verspaetung gegenueber dem Plan, negativ bei Verfruehung
	 * @param realtime      ob zu dieser Fahrt eine Echtzeitmeldung vorliegt
	 * @param interchanges  Anzahl der Umstiege
	 * @param legs          Teilstrecken in Reihenfolge, inklusive Fusswege
	 */
	public record Connection(
			Instant departure,
			Instant planned,
			int delayMinutes,
			boolean realtime,
			Instant arrival,
			int durationMinutes,
			int interchanges,
			List<Leg> legs
	) {
	}

	/**
	 * @param line    Linienbezeichnung wie "91" oder "S2", bei Fusswegen leer
	 * @param product Verkehrsmittel wie "Bus" oder "S-Bahn", bei Fusswegen "Fußweg"
	 * @param towards Fahrtrichtung, also das Ziel der Linie — nicht das eigene
	 * @param walk    Fussweg statt Fahrt
	 */
	public record Leg(
			String line,
			String product,
			String towards,
			String from,
			String to,
			String platform,
			Instant departure,
			Instant arrival,
			int durationMinutes,
			boolean walk
	) {
	}
}
