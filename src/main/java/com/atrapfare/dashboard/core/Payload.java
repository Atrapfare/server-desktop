package com.atrapfare.dashboard.core;

import java.time.Instant;

public record Payload<T>(
		String id,
		Status status,
		Instant lastUpdated,
		T data,
		String error
) {

	public static <T> Payload<T> ok(String id, T data, Instant lastUpdated) {
		return new Payload<>(id, Status.OK, lastUpdated, data, null);
	}

	public static <T> Payload<T> error(String id, String error) {
		return new Payload<>(id, Status.ERROR, null, null, error);
	}

	/**
	 * Behaelt Daten und Zeitstempel des letzten Erfolgs. Ein veralteter Wert ist
	 * fuer ein Dashboard brauchbarer als eine leere Kachel, solange er als
	 * veraltet erkennbar bleibt.
	 */
	public Payload<T> asStale(String error) {
		return new Payload<>(id, Status.STALE, lastUpdated, data, error);
	}
}
