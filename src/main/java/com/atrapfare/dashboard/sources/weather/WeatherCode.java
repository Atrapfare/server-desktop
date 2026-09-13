package com.atrapfare.dashboard.sources.weather;

import java.util.HashMap;
import java.util.Map;

public enum WeatherCode {

	KLAR(0, "Klar"),
	UEBERWIEGEND_KLAR(1, "Überwiegend klar"),
	TEILS_BEWOELKT(2, "Teils bewölkt"),
	BEDECKT(3, "Bedeckt"),
	NEBEL(45, "Nebel"),
	REIFNEBEL(48, "Reifnebel"),
	NIESELREGEN_LEICHT(51, "Leichter Nieselregen"),
	NIESELREGEN(53, "Nieselregen"),
	NIESELREGEN_STARK(55, "Starker Nieselregen"),
	GEFRIERENDER_NIESEL_LEICHT(56, "Leichter gefrierender Niesel"),
	GEFRIERENDER_NIESEL(57, "Gefrierender Niesel"),
	REGEN_LEICHT(61, "Leichter Regen"),
	REGEN(63, "Regen"),
	REGEN_STARK(65, "Starker Regen"),
	GEFRIERENDER_REGEN_LEICHT(66, "Leichter gefrierender Regen"),
	GEFRIERENDER_REGEN(67, "Gefrierender Regen"),
	SCHNEE_LEICHT(71, "Leichter Schneefall"),
	SCHNEE(73, "Schneefall"),
	SCHNEE_STARK(75, "Starker Schneefall"),
	SCHNEEGRIESEL(77, "Schneegriesel"),
	SCHAUER_LEICHT(80, "Leichte Schauer"),
	SCHAUER(81, "Schauer"),
	SCHAUER_STARK(82, "Starke Schauer"),
	SCHNEESCHAUER_LEICHT(85, "Leichte Schneeschauer"),
	SCHNEESCHAUER(86, "Schneeschauer"),
	GEWITTER(95, "Gewitter"),
	GEWITTER_HAGEL_LEICHT(96, "Gewitter mit leichtem Hagel"),
	GEWITTER_HAGEL(99, "Gewitter mit Hagel"),
	UNBEKANNT(-1, "Unbekannt");

	private static final Map<Integer, WeatherCode> BY_CODE = new HashMap<>();

	static {
		for (WeatherCode value : values()) {
			BY_CODE.put(value.code, value);
		}
	}

	private final int code;

	private final String text;

	WeatherCode(int code, String text) {
		this.code = code;
		this.text = text;
	}

	public static WeatherCode of(int code) {
		return BY_CODE.getOrDefault(code, UNBEKANNT);
	}

	public int code() {
		return code;
	}

	public String text() {
		return text;
	}
}
