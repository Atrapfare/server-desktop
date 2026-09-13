package com.atrapfare.dashboard.sources.weather;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

record OpenMeteoResponse(
		Current current,
		Daily daily
) {

	record Current(
			@JsonProperty("temperature_2m") double temperature,
			@JsonProperty("apparent_temperature") double apparentTemperature,
			@JsonProperty("weather_code") int weatherCode
	) {
	}

	record Daily(
			@JsonProperty("temperature_2m_max") List<Double> maxTemperature,
			@JsonProperty("temperature_2m_min") List<Double> minTemperature,
			@JsonProperty("precipitation_probability_max") List<Integer> precipitationProbability,
			List<LocalDateTime> sunrise,
			List<LocalDateTime> sunset
	) {
	}
}
