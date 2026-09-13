package com.atrapfare.dashboard.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.atrapfare.dashboard.core.CollectorRegistry;
import com.atrapfare.dashboard.core.Payload;
import com.atrapfare.dashboard.core.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WidgetController {

	private final CollectorRegistry registry;

	public WidgetController(CollectorRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/widgets")
	public Map<String, Payload<?>> widgets() {
		return registry.payloads();
	}

	@GetMapping("/health")
	public List<Health> health() {
		Instant now = Instant.now();
		return registry.collectors().stream()
				.map(collector -> {
					Payload<?> payload = registry.payloads().get(collector.id());
					if (payload == null) {
						return new Health(collector.id(), null, null);
					}
					Long age = (payload.lastUpdated() == null) ? null
							: Duration.between(payload.lastUpdated(), now).toSeconds();
					return new Health(collector.id(), payload.status(), age);
				})
				.toList();
	}

	public record Health(String id, Status status, Long ageSeconds) {
	}
}
