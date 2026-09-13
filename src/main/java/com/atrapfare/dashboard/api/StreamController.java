package com.atrapfare.dashboard.api;

import com.atrapfare.dashboard.core.DashboardEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class StreamController {

	private final DashboardEventPublisher publisher;

	public StreamController(DashboardEventPublisher publisher) {
		this.publisher = publisher;
	}

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream() {
		// Unbegrenzt: der Stream soll dauerhaft offen bleiben. Gegen stille
		// Verbindungsabbrueche schickt der Publisher alle 30 s einen Heartbeat.
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		publisher.register(emitter);
		return emitter;
	}
}
