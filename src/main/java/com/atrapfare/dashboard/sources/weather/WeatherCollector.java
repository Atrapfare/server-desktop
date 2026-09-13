package com.atrapfare.dashboard.sources.weather;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeatherCollector implements Collector<WeatherData> {

	private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

	private final DashboardProperties.Weather properties;

	private final RestClient restClient;

	public WeatherCollector(DashboardProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties.weather();
		this.restClient = restClientBuilder.build();
	}

	@Override
	public String id() {
		return "weather";
	}

	@Override
	public Duration interval() {
		return properties.interval();
	}

	@Override
	public WeatherData collect() {
		OpenMeteoResponse response = restClient.get()
				.uri(BASE_URL, uri -> uri
						.queryParam("latitude", properties.latitude())
						.queryParam("longitude", properties.longitude())
						.queryParam("current", "temperature_2m,apparent_temperature,weather_code")
						.queryParam("daily", "temperature_2m_max,temperature_2m_min,"
								+ "precipitation_probability_max,sunrise,sunset")
						// timezone=auto laesst Open-Meteo die Tageswerte und die
						// Sonnenzeiten in der Zeitzone der Koordinaten liefern.
						.queryParam("timezone", "auto")
						.queryParam("forecast_days", 1)
						.build())
				.retrieve()
				.body(OpenMeteoResponse.class);

		if (response == null || response.current() == null || response.daily() == null) {
			throw new IllegalStateException("Open-Meteo lieferte keine verwertbare Antwort");
		}

		OpenMeteoResponse.Current current = response.current();
		OpenMeteoResponse.Daily daily = response.daily();
		WeatherCode code = WeatherCode.of(current.weatherCode());

		return new WeatherData(
				current.temperature(),
				current.apparentTemperature(),
				code.code(),
				code.text(),
				first(daily.maxTemperature(), Double.NaN),
				first(daily.minTemperature(), Double.NaN),
				first(daily.precipitationProbability(), 0),
				time(first(daily.sunrise(), null)),
				time(first(daily.sunset(), null)));
	}

	private static <T> T first(List<T> values, T fallback) {
		if (values == null || values.isEmpty() || values.getFirst() == null) {
			return fallback;
		}
		return values.getFirst();
	}

	private static LocalTime time(LocalDateTime value) {
		return (value == null) ? null : value.toLocalTime();
	}
}
