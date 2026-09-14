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
		@NestedConfigurationProperty Vps vps
) {

	public record Weather(
			double latitude,
			double longitude,
			Duration interval
	) {
	}

	public record Calendar(
			String icalUrl,
			Duration interval,
			Duration lookAhead
	) {
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
}
