package com.atrapfare.dashboard.sources.host;

import java.time.Instant;
import java.util.List;

/**
 * Werte des Rechners, auf dem das Dashboard selbst laeuft. Bewusst in derselben
 * Form wie {@code VpsStats}, damit beide Kacheln dieselbe Darstellung benutzen.
 *
 * @param uptimeSeconds {@code null}, wenn die Laufzeit des Systems nicht
 *                      ermittelbar ist — etwa ausserhalb von Linux
 * @param load          leer, wenn der Kernel keine Lastmittel fuehrt
 */
public record HostStats(
		String hostname,
		Long uptimeSeconds,
		List<Double> load,
		int cpuCount,
		Memory memory,
		Disk disk,
		Instant timestamp
) {

	public record Memory(long totalMb, long usedMb) {
	}

	public record Disk(long totalGb, long usedGb) {
	}
}
