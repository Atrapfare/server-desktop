package com.atrapfare.dashboard.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "dashboard")
public record DashboardProperties(
		@NestedConfigurationProperty Weather weather,
		@NestedConfigurationProperty Calendar calendar
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
}
