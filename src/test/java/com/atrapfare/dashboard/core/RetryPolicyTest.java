package com.atrapfare.dashboard.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ein Fehlschlag darf bei langen Intervallen nicht bis zum naechsten regulaeren
 * Abruf stehen bleiben - die Nachrichten waeren sonst eine Stunde lang leer.
 */
class RetryPolicyTest {

	private static Collector<String> collector(Duration interval, AtomicInteger calls, boolean fail) {
		return new Collector<>() {
			@Override
			public String id() {
				return "test";
			}

			@Override
			public Duration interval() {
				return interval;
			}

			@Override
			public String collect() {
				calls.incrementAndGet();
				if (fail) {
					throw new IllegalStateException("kaputt");
				}
				return "wert";
			}
		};
	}

	private CollectorRegistry registry() {
		CollectorRegistry registry = new CollectorRegistry(List.of(), new DashboardEventPublisher());
		registry.start();
		return registry;
	}

	@Test
	void langesIntervallBekommtEinenZweitenAnlauf() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CollectorRegistry registry = registry();
		try {
			registry.refresh(collector(Duration.ofHours(1), calls, true));
			// Der zweite Anlauf ist eingeplant, laeuft aber erst in 60 s - hier
			// zaehlt nur, dass der erste Versuch genau einmal stattfand.
			assertThat(calls.get()).isEqualTo(1);
			assertThat(registry.payloads().get("test").status()).isEqualTo(Status.ERROR);
		}
		finally {
			registry.stop();
		}
	}

	@Test
	void kurzesIntervallBekommtKeinen() {
		AtomicInteger calls = new AtomicInteger();
		CollectorRegistry registry = registry();
		try {
			// 30 s liegen unter der dreifachen Wartezeit - der regulaere Abruf
			// kommt ohnehin gleich, eine Wiederholung waere nur Zusatzlast.
			registry.refresh(collector(Duration.ofSeconds(30), calls, true));
			assertThat(calls.get()).isEqualTo(1);
		}
		finally {
			registry.stop();
		}
	}

	@Test
	void erfolgreicherAbrufPlantNichtsNach() {
		AtomicInteger calls = new AtomicInteger();
		CollectorRegistry registry = registry();
		try {
			registry.refresh(collector(Duration.ofHours(1), calls, false));
			assertThat(calls.get()).isEqualTo(1);
			assertThat(registry.payloads().get("test").status()).isEqualTo(Status.OK);
		}
		finally {
			registry.stop();
		}
	}
}
