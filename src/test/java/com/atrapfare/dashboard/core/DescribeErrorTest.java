package com.atrapfare.dashboard.core;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Fehlermeldung landet unveraendert in der Kachel. Netzwerkfehler kommen
 * mehrfach verpackt an, und die aeusserste Schicht nennt haeufig nur die
 * Adresse - der Grund steckt weiter innen.
 */
class DescribeErrorTest {

	private static String describe(Exception failure) {
		CollectorRegistry registry = new CollectorRegistry(List.of(), new DashboardEventPublisher());
		Collector<String> collector = new Collector<>() {
			@Override
			public String id() {
				return "test";
			}

			@Override
			public Duration interval() {
				return Duration.ofMinutes(1);
			}

			@Override
			public String collect() throws Exception {
				throw failure;
			}
		};
		registry.refresh(collector);
		return registry.payloads().get("test").error();
	}

	@Test
	void ursacheOhneMeldungWirdBeimNamenGenannt() {
		String error = describe(new ResourceAccessException(
				"I/O error on GET request for \"https://api.beispiel.de/v1\": null",
				new ClosedChannelException()));

		assertThat(error)
				.contains("api.beispiel.de")
				.contains("ClosedChannelException");
	}

	@Test
	void ursacheMitMeldungWirdAngehaengt() {
		String error = describe(new ResourceAccessException(
				"I/O error on GET request for \"https://api.beispiel.de/v1\": null",
				new IOException("aussen", new ConnectException("Connection refused"))));

		assertThat(error).contains("ConnectException: Connection refused");
	}

	@Test
	void ohneVerschachtelungBleibtDieMeldungWieSieIst() {
		String error = describe(new IllegalStateException("Agent lieferte keine Daten"));

		assertThat(error).isEqualTo("IllegalStateException: Agent lieferte keine Daten");
	}

	@Test
	void bereitsEnthalteneUrsacheWirdNichtDoppeltGenannt() {
		ConnectException cause = new ConnectException("Connection refused");
		String error = describe(new ResourceAccessException(
				"ConnectException: Connection refused", cause));

		assertThat(error).containsOnlyOnce("Connection refused");
	}
}
