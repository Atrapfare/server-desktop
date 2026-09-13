package com.atrapfare.dashboard.core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class DashboardEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(DashboardEventPublisher.class);

	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	public void register(SseEmitter emitter) {
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		log.debug("SSE-Client verbunden, jetzt {} aktiv", emitters.size());
	}

	public void publish(Payload<?> payload) {
		send(SseEmitter.event().name("widget").data(payload));
	}

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
