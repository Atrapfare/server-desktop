package com.atrapfare.dashboard.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorRegistryTest {

	private final AtomicReference<Object> next = new AtomicReference<>();

	private final Collector<Object> collector = new Collector<>() {
		@Override
		public String id() {
			return "test";
		}

		@Override
		public Duration interval() {
			return Duration.ofMinutes(1);
		}

		@Override
		public Object collect() throws Exception {
			Object value = next.get();
			if (value instanceof Exception e) {
				throw e;
			}
			return value;
		}
	};

	private final CollectorRegistry registry =
			new CollectorRegistry(List.of(collector), new DashboardEventPublisher());

	@Test
	void erfolgreicherAbrufLiefertOk() {
		next.set("wert");
		registry.refresh(collector);

		Payload<?> payload = registry.payloads().get("test");
		assertThat(payload.status()).isEqualTo(Status.OK);
		assertThat(payload.data()).isEqualTo("wert");
		assertThat(payload.error()).isNull();
		assertThat(payload.lastUpdated()).isNotNull();
	}

	@Test
	void fehlerOhneVorherigenErfolgLiefertError() {
		next.set(new IllegalStateException("kaputt"));
		registry.refresh(collector);

		Payload<?> payload = registry.payloads().get("test");
		assertThat(payload.status()).isEqualTo(Status.ERROR);
		assertThat(payload.data()).isNull();
		assertThat(payload.lastUpdated()).isNull();
		assertThat(payload.error()).contains("kaputt");
	}

	@Test
	void fehlerNachErfolgBehaeltDatenUndZeitstempel() {
		next.set("wert");
		registry.refresh(collector);
		Payload<?> erfolg = registry.payloads().get("test");

		next.set(new IllegalStateException("netz weg"));
		registry.refresh(collector);

		Payload<?> payload = registry.payloads().get("test");
		assertThat(payload.status()).isEqualTo(Status.STALE);
		assertThat(payload.data()).isEqualTo("wert");
		assertThat(payload.lastUpdated()).isEqualTo(erfolg.lastUpdated());
		assertThat(payload.error()).contains("netz weg");
	}

	@Test
	void wiederholterFehlerBleibtStale() {
		next.set("wert");
		registry.refresh(collector);
		next.set(new IllegalStateException("erster"));
		registry.refresh(collector);
		next.set(new IllegalStateException("zweiter"));
		registry.refresh(collector);

		Payload<?> payload = registry.payloads().get("test");
		assertThat(payload.status()).isEqualTo(Status.STALE);
		assertThat(payload.data()).isEqualTo("wert");
		assertThat(payload.error()).contains("zweiter");
	}

	@Test
	void fehlerNachDauerhaftemErrorBleibtError() {
		next.set(new IllegalStateException("erster"));
		registry.refresh(collector);
		next.set(new IllegalStateException("zweiter"));
		registry.refresh(collector);

		Payload<?> payload = registry.payloads().get("test");
		assertThat(payload.status()).isEqualTo(Status.ERROR);
		assertThat(payload.data()).isNull();
	}

	@Test
	void erholungNachFehlerLiefertWiederOk() {
		next.set("alt");
		registry.refresh(collector);
		next.set(new IllegalStateException("kurz weg"));
		registry.refresh(collector);
		next.set("neu");
		registry.refresh(collector);

		Payload<?> payload = registry.payloads().get("test");
		assertThat(payload.status()).isEqualTo(Status.OK);
		assertThat(payload.data()).isEqualTo("neu");
		assertThat(payload.error()).isNull();
	}
}
