package com.atrapfare.dashboard.sources.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.atrapfare.dashboard.config.DashboardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class HostCollectorTest {

	@TempDir
	Path proc;

	@BeforeEach
	void fakeProcFs() throws IOException {
		write("uptime", "1483920.55 5872341.12\n");
		write("loadavg", "1.42 0.98 0.71 2/431 18732\n");
		write("meminfo", """
				MemTotal:        8152064 kB
				MemFree:          412360 kB
				MemAvailable:    1794048 kB
				Buffers:           98304 kB
				""");
		Files.createDirectories(proc.resolve("sys/kernel"));
		write("sys/kernel/hostname", "heimserver\n");
	}

	private void write(String name, String content) throws IOException {
		Files.writeString(proc.resolve(name), content, StandardCharsets.UTF_8);
	}

	private HostStats collect(String name) throws Exception {
		DashboardProperties properties = new DashboardProperties(null, null, null, null,
				new DashboardProperties.Host(name, proc.toString(),
						System.getProperty("user.dir"), Duration.ofSeconds(15)),
				null);
		return new HostCollector(properties).collect();
	}

	@Test
	void laufzeitKommtAusProcUptime() throws Exception {
		assertThat(collect("").uptimeSeconds()).isEqualTo(1_483_920L);
	}

	@Test
	void alleDreiLastmittelWerdenGelesen() throws Exception {
		assertThat(collect("").load()).containsExactly(1.42, 0.98, 0.71);
	}

	@Test
	void belegterSpeicherRechnetMitMemAvailable() throws Exception {
		HostStats.Memory memory = collect("").memory();

		// 8152064 kB / 1024 = 7961 MB
		assertThat(memory.totalMb()).isEqualTo(7961);
		// 8152064 kB - 1794048 kB = 6358016 kB belegt
		assertThat(memory.usedMb()).isEqualTo(6209);
	}

	@Test
	void ohneKonfiguriertenNamenGiltDerDesKernels() throws Exception {
		assertThat(collect("").hostname()).isEqualTo("heimserver");
	}

	@Test
	void konfigurierterNameGehtVor() throws Exception {
		assertThat(collect("Dachboden").hostname()).isEqualTo("Dachboden");
	}

	@Test
	void ohneProcBleibenLaufzeitUndLastLeer() throws Exception {
		DashboardProperties properties = new DashboardProperties(null, null, null, null,
				new DashboardProperties.Host("test", proc.resolve("fehlt").toString(),
						System.getProperty("user.dir"), Duration.ofSeconds(15)),
				null);

		HostStats stats = new HostCollector(properties).collect();

		assertThat(stats.uptimeSeconds()).isNull();
		// Die JVM liefert unter Linux ein Lastmittel, unter Windows keines -
		// beides ist zulaessig, geraten wird nichts.
		assertThat(stats.load()).hasSizeLessThan(2);
		// Speicher und Platte stehen auch ohne /proc zur Verfuegung.
		assertThat(stats.memory().totalMb()).isPositive();
		assertThat(stats.disk().totalGb()).isPositive();
	}

	@Test
	void plattenbelegungBleibtInnerhalbDerGesamtgroesse() throws Exception {
		HostStats.Disk disk = collect("").disk();

		assertThat(disk.usedGb()).isBetween(0L, disk.totalGb());
	}
}
