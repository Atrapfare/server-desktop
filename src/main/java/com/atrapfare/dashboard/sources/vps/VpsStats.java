package com.atrapfare.dashboard.sources.vps;

import java.time.Instant;
import java.util.List;

public record VpsStats(
		String hostname,
		long uptimeSeconds,
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
