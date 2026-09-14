package com.atrapfare.dashboard.sources.transit;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ausschnitt der EFA-Antwort (rapidJSON), auf die im Dashboard benutzten Felder
 * beschnitten. Alles Uebrige ignoriert Jackson.
 */
public record EfaTripResponse(
		List<Journey> journeys
) {

	public record Journey(
			Integer interchanges,
			List<Leg> legs
	) {
	}

	public record Leg(
			Integer duration,
			Point origin,
			Point destination,
			Transportation transportation
	) {
	}

	public record Point(
			String name,
			Instant departureTimePlanned,
			Instant departureTimeEstimated,
			Instant arrivalTimePlanned,
			Instant arrivalTimeEstimated,
			Properties properties
	) {

		public record Properties(String platform) {
		}
	}

	public record Transportation(
			String number,
			String disassembledName,
			Product product,
			Destination destination
	) {

		/**
		 * @param productClass Verkehrsmittelklasse der EFA. 99 und 100 sind
		 *                     Fusswege, alles darunter sind Fahrten.
		 */
		public record Product(
				@JsonProperty("class") Integer productClass,
				String name
		) {
		}

		public record Destination(String name) {
		}
	}
}
