package com.atrapfare.dashboard.core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class DashboardEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(DashboardEventPublisher.class);

	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	public void register(SseEmitter emitter) {
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		try {
			// Erst dieser Write schickt die Antwort-Header raus. Ohne ihn haengt
			// der Client bis zum ersten Heartbeat, ohne zu wissen, dass die
			// Verbindung steht - EventSource.onopen feuert dann erst nach 30 s.
			emitter.send(SseEmitter.event().comment("verbunden"));
		}
		catch (IOException e) {
			emitter.completeWithError(e);
			return;
		}
		emitters.add(emitter);
		log.debug("SSE-Client verbunden, jetzt {} aktiv", emitters.size());
	}

	public void publish(Payload<?> payload) {
		send(SseEmitter.event().name("widget").data(payload));
	}

	@Scheduled(fixedRate = 30_000)
	public void heartbeat() {
		send(SseEmitter.event().comment("ping"));
	}

	public int subscriberCount() {
		return emitters.size();
	}

	/**
	 * Ein toter Emitter meldet sich nicht von selbst ab - der Fehler faellt erst
	 * beim naechsten Schreibversuch auf. Darum hier entfernen statt auf
	 * onError zu warten, das bei bereits geschlossenem Socket ausbleiben kann.
	 */
	private void send(SseEmitter.SseEventBuilder event) {
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(event);
			}
			catch (IOException | IllegalStateException e) {
				emitters.remove(emitter);
				emitter.completeWithError(e);
			}
		}
	}
}
