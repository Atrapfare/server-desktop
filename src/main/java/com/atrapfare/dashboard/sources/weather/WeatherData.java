package com.atrapfare.dashboard.sources.weather;

import java.time.LocalTime;

public record WeatherData(
		double temperature,
		double apparentTemperature,
		int weatherCode,
		String condition,
		double maxTemperature,
		double minTemperature,
		int precipitationProbability,
		LocalTime sunrise,
		LocalTime sunset
) {
}
