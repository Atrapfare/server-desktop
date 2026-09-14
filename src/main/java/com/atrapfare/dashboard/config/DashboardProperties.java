package com.atrapfare.dashboard.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "dashboard")
public record DashboardProperties(
		@NestedConfigurationProperty Weather weather,
		@NestedConfigurationProperty Calendar calendar,
		@NestedConfigurationProperty News news,
		@NestedConfigurationProperty Vps vps,
		@NestedConfigurationProperty Host host,
		@NestedConfigurationProperty Transit transit
) {

	public record Weather(
			double latitude,
			double longitude,
			Duration interval
	) {
	}

	/**
	 * @param icalUrl Einzelne Quelle. Bleibt fuer bestehende Installationen
	 *                erhalten und zaehlt als weitere Quelle neben {@code sources}.
	 * @param sources Beliebig viele benannte Kalender. Der Name steht an den
	 *                Terminen, sobald mehr als eine Quelle Termine liefert.
	 */
	public record Calendar(
			String icalUrl,
			List<Source> sources,
			Duration interval,
			Duration lookAhead
	) {

		public record Source(String name, String url) {
		}
	}

	public record News(
			Duration interval,
			int maxItems,
			List<Feed> feeds
	) {

		public record Feed(String name, String url) {
		}
	}

	public record Vps(
			String url,
			Duration interval
	) {
	}

	/**
	 * Der Rechner, auf dem diese Anwendung selbst laeuft.
	 *
	 * @param name     Anzeigename. Leer: der Rechnername des Systems. Im Container
	 *                 ist das die Container-ID, deshalb dort besser setzen.
	 * @param procPath Wurzel der Kernel-Werte. Im Container zeigt {@code /proc}
	 *                 bereits die Werte des Hosts — Last, Speicher und Laufzeit
	 *                 sind nicht pro Container getrennt.
	 * @param diskPath Pfad auf dem Dateisystem, dessen Belegung gemeldet wird.
	 *                 Im Container das eigene Overlay, solange kein Verzeichnis
	 *                 des Hosts eingehaengt ist.
	 */
	public record Host(
			String name,
			String procPath,
			String diskPath,
			Duration interval
	) {
	}

	/**
	 * Abfahrten des VVS als Verbindungsauskunft von {@code origin} nach
	 * {@code destination}. Die Auskunft statt eines reinen Abfahrtsmonitors,
	 * weil nur sie sagt, welche Abfahrt tatsaechlich ans Ziel fuehrt.
	 *
	 * @param origin      Haltestellen-ID der Abfahrt, z. B. {@code de:08111:2420}
	 * @param destination Haltestellen-ID des Ziels
	 * @param results     Anzahl der angezeigten Verbindungen
	 */
	public record Transit(
			String baseUrl,
			String origin,
			String destination,
			int results,
			Duration interval
	) {
	}
}
