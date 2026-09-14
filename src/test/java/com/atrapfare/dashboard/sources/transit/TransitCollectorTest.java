package com.atrapfare.dashboard.sources.transit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.atrapfare.dashboard.config.DashboardProperties;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitCollectorTest {

	private static final Instant NOW = Instant.parse("2026-09-15T06:00:00Z");

	/**
	 * Nachbau einer echten Antwort von www3.vvs.de: eine bereits abgefahrene
	 * Verbindung, eine direkte Busfahrt mit Echtzeitmeldung und eine mit
	 * Fussweg und Umstieg.
	 */
	private static final String JSON = """
			{
			  "journeys": [
			    {
			      "interchanges": 0,
			      "legs": [{
			        "duration": 1080,
			        "origin": {
			          "name": "Laihle",
			          "departureTimePlanned": "2026-09-15T05:41:00Z",
			          "properties": { "platform": "3" }
			        },
			        "destination": { "name": "Universität", "arrivalTimePlanned": "2026-09-15T05:59:00Z" },
			        "transportation": {
			          "number": "91", "disassembledName": "91",
			          "product": { "class": 5, "name": "Bus" },
			          "destination": { "name": "Büsnauer Platz" }
			        }
			      }]
			    },
			    {
			      "interchanges": 0,
			      "legs": [{
			        "duration": 1080,
			        "origin": {
			          "name": "Laihle",
			          "departureTimePlanned": "2026-09-15T06:01:00Z",
			          "departureTimeEstimated": "2026-09-15T06:04:00Z",
			          "properties": { "platform": "3" }
			        },
			        "destination": {
			          "name": "Universität",
			          "arrivalTimePlanned": "2026-09-15T06:19:00Z",
			          "arrivalTimeEstimated": "2026-09-15T06:22:00Z"
			        },
			        "transportation": {
			          "number": "91", "disassembledName": "91",
			          "product": { "class": 5, "name": "Bus" },
			          "destination": { "name": "Büsnauer Platz" }
			        }
			      }]
			    },
			    {
			      "interchanges": 1,
			      "legs": [
			        {
			          "duration": 480,
			          "origin": { "name": "Laihle", "departureTimePlanned": "2026-09-15T06:35:00Z" },
			          "destination": { "name": "Millöckerstraße", "arrivalTimePlanned": "2026-09-15T06:43:00Z" },
			          "transportation": { "product": { "class": 100, "name": "footpath" } }
			        },
			        {
			          "duration": 900,
			          "origin": {
			            "name": "Millöckerstraße",
			            "departureTimePlanned": "2026-09-15T06:45:00Z",
			            "properties": { "platform": "2" }
			          },
			          "destination": { "name": "Universität", "arrivalTimePlanned": "2026-09-15T07:00:00Z" },
			          "transportation": {
			            "number": "U2", "disassembledName": "U2",
			            "product": { "class": 4, "name": "Stadtbahn" },
			            "destination": { "name": "Neugereut" }
			          }
			        }
			      ]
			    }
			  ]
			}
			""";

	private TransitData map(int results) {
		EfaTripResponse response = JsonMapper.builder().build().readValue(JSON, EfaTripResponse.class);
		return collector(results).map(response, NOW);
	}

	private TransitCollector collector(int results) {
		DashboardProperties properties = new DashboardProperties(null, null, null, null, null,
				new DashboardProperties.Transit("http://unbenutzt", "de:08111:2420", "de:08111:6008",
						results, Duration.ofMinutes(2)));
		return new TransitCollector(properties, RestClient.builder());
	}

	@Test
	void bereitsAbgefahreneVerbindungFaelltRaus() {
		TransitData data = map(5);

		assertThat(data.connections())
				.extracting(TransitData.Connection::departure)
				.containsExactly(Instant.parse("2026-09-15T06:04:00Z"), Instant.parse("2026-09-15T06:35:00Z"));
	}

	@Test
	void echtzeitAbfahrtGiltUndVerspaetungWirdBerechnet() {
		TransitData.Connection first = map(5).connections().getFirst();

		assertThat(first.realtime()).isTrue();
		assertThat(first.planned()).isEqualTo(Instant.parse("2026-09-15T06:01:00Z"));
		assertThat(first.departure()).isEqualTo(Instant.parse("2026-09-15T06:04:00Z"));
		assertThat(first.delayMinutes()).isEqualTo(3);
		// Dauer ab der tatsaechlichen Abfahrt, nicht ab dem Plan.
		assertThat(first.durationMinutes()).isEqualTo(18);
	}

	@Test
	void ohneEchtzeitBleibtDerPlanUndDieVerspaetungIstNull() {
		TransitData.Connection second = map(5).connections().get(1);

		assertThat(second.realtime()).isFalse();
		assertThat(second.delayMinutes()).isZero();
		assertThat(second.departure()).isEqualTo(second.planned());
	}

	@Test
	void fusswegWirdAlsSolcherErkannt() {
		List<TransitData.Leg> legs = map(5).connections().get(1).legs();

		assertThat(legs).hasSize(2);
		assertThat(legs.getFirst().walk()).isTrue();
		assertThat(legs.getFirst().line()).isEmpty();
		assertThat(legs.getFirst().product()).isEqualTo("Fußweg");
		assertThat(legs.get(1).walk()).isFalse();
		assertThat(legs.get(1).line()).isEqualTo("U2");
		assertThat(legs.get(1).towards()).isEqualTo("Neugereut");
		assertThat(legs.get(1).platform()).isEqualTo("2");
	}

	@Test
	void nichtMehrVerbindungenAlsGewuenscht() {
		assertThat(map(1).connections()).hasSize(1);
	}

	@Test
	void namenStammenAusDerAntwort() {
		TransitData data = map(5);

		assertThat(data.origin()).isEqualTo("Laihle");
		assertThat(data.destination()).isEqualTo("Universität");
	}

	@Test
	void antwortOhneVerbindungenIstEinFehler() {
		assertThatThrownBy(() -> collector(5).map(new EfaTripResponse(null), NOW))
				.isInstanceOf(IllegalStateException.class);
	}
}
