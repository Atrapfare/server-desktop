package com.atrapfare.dashboard.sources.host;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import org.springframework.stereotype.Component;

/**
 * Liest die Werte des eigenen Rechners direkt aus {@code /proc}, genau wie der
 * VPS-Agent es auf der Gegenseite tut.
 *
 * Im Container ist das der richtige Weg: Last, Speicher und Laufzeit sind im
 * Kernel nicht pro Container getrennt, {@code /proc} zeigt dort also die Werte
 * des Hosts. Nur das Dateisystem ist getrennt — welche Platte gemeldet wird,
 * entscheidet deshalb {@code dashboard.host.disk-path}.
 *
 * Fehlt {@code /proc} — etwa bei der Entwicklung unter Windows — treten die
 * Werte der JVM an seine Stelle. Was sich so nicht bestimmen laesst, bleibt
 * leer, statt geraten zu werden.
 */
@Component
public class HostCollector implements Collector<HostStats> {

	private static final long KIB_PER_MIB = 1024;

	private static final long BYTES_PER_GIB = 1024L * 1024 * 1024;

	private final DashboardProperties.Host properties;

	public HostCollector(DashboardProperties properties) {
		this.properties = properties.host();
	}

	@Override
	public String id() {
		return "host";
	}

	@Override
	public Duration interval() {
		return properties.interval();
	}

	@Override
	public HostStats collect() throws IOException {
		Path proc = Path.of(properties.procPath());
		OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

		return new HostStats(
				hostname(proc),
				uptimeSeconds(proc),
				load(proc, os),
				Math.max(1, os.getAvailableProcessors()),
				memory(proc, os),
				disk(),
				Instant.now());
	}

	private String hostname(Path proc) {
		if (properties.name() != null && !properties.name().isBlank()) {
			return properties.name();
		}
		String fromKernel = readFirstLine(proc.resolve("sys/kernel/hostname"));
		if (fromKernel != null && !fromKernel.isBlank()) {
			return fromKernel.strip();
		}
		try {
			return InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e) {
			return "unbekannt";
		}
	}

	private Long uptimeSeconds(Path proc) {
		String line = readFirstLine(proc.resolve("uptime"));
		if (line == null) {
			return null;
		}
		String[] fields = line.strip().split("\\s+");
		try {
			return (long) Double.parseDouble(fields[0]);
		}
		catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}

	/**
	 * Die drei Mittelwerte ueber 1, 5 und 15 Minuten. Ausserhalb von Linux
	 * liefert die JVM hoechstens den ersten; fehlt auch der, bleibt die Liste
	 * leer und die Kachel zeigt die Last gar nicht erst an.
	 */
	private List<Double> load(Path proc, OperatingSystemMXBean os) {
		String line = readFirstLine(proc.resolve("loadavg"));
		if (line != null) {
			String[] fields = line.strip().split("\\s+");
			List<Double> values = new ArrayList<>(3);
			for (int i = 0; i < Math.min(3, fields.length); i++) {
				try {
					values.add(Double.parseDouble(fields[i]));
				}
				catch (NumberFormatException e) {
					return List.of();
				}
			}
			return List.copyOf(values);
		}

		double average = os.getSystemLoadAverage();
		return (average < 0) ? List.of() : List.of(average);
	}

	private HostStats.Memory memory(Path proc, OperatingSystemMXBean os) throws IOException {
		Path meminfo = proc.resolve("meminfo");
		if (Files.isReadable(meminfo)) {
			Map<String, Long> values = new HashMap<>();
			for (String line : Files.readAllLines(meminfo)) {
				int colon = line.indexOf(':');
				if (colon < 0) {
					continue;
				}
				String[] rest = line.substring(colon + 1).strip().split("\\s+");
				try {
					values.put(line.substring(0, colon), Long.parseLong(rest[0]));
				}
				catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					// Zeilen ohne Zahl gibt es in /proc/meminfo nicht; falls doch,
					// ist eine ausgelassene Zeile besser als ein Abbruch.
				}
			}

			long totalKb = values.getOrDefault("MemTotal", 0L);
			// MemAvailable beruecksichtigt zurueckgewinnbaren Cache und beschreibt
			// damit besser, was wirklich belegt ist, als MemFree.
			long availableKb = values.getOrDefault("MemAvailable", values.getOrDefault("MemFree", 0L));
			return new HostStats.Memory(totalKb / KIB_PER_MIB, (totalKb - availableKb) / KIB_PER_MIB);
		}

		if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
			long total = sun.getTotalMemorySize();
			long free = sun.getFreeMemorySize();
			return new HostStats.Memory(total / (KIB_PER_MIB * KIB_PER_MIB),
					(total - free) / (KIB_PER_MIB * KIB_PER_MIB));
		}
		return new HostStats.Memory(0, 0);
	}

	private HostStats.Disk disk() throws IOException {
		FileStore store = Files.getFileStore(Path.of(properties.diskPath()));
		long total = store.getTotalSpace();
		// Wie df: belegt ist alles ausser den freien Bloecken, inklusive der fuer
		// root reservierten. getUsableSpace liesse diese Reserve aus.
		long used = total - store.getUnallocatedSpace();
		return new HostStats.Disk(total / BYTES_PER_GIB, used / BYTES_PER_GIB);
	}

	private static String readFirstLine(Path path) {
		try {
			if (!Files.isReadable(path)) {
				return null;
			}
			return Files.readAllLines(path).stream().findFirst().orElse(null);
		}
		catch (IOException e) {
			return null;
		}
	}
}
