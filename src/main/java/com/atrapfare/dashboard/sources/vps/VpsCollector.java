package com.atrapfare.dashboard.sources.vps;

import java.time.Duration;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VpsCollector implements Collector<VpsStats> {

	private final DashboardProperties.Vps properties;

	private final RestClient restClient;

	public VpsCollector(DashboardProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties.vps();
		this.restClient = restClientBuilder.build();
	}

	@Override
	public String id() {
		return "vps";
	}

	@Override
	public Duration interval() {
		return properties.interval();
	}

	@Override
	public VpsStats collect() {
		VpsStats stats = restClient.get()
				.uri(properties.url())
				.retrieve()
				.body(VpsStats.class);

		if (stats == null) {
			throw new IllegalStateException("Agent lieferte keine Daten");
		}
		return stats;
	}
}
