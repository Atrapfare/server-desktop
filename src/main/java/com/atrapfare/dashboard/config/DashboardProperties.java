package com.atrapfare.dashboard.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "dashboard")
public record DashboardProperties(
		@NestedConfigurationProperty Weather weather
) {

	public record Weather(
			double latitude,
			double longitude,
			Duration interval
	) {
	}
}
