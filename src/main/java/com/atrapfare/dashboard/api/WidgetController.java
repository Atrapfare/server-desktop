package com.atrapfare.dashboard.api;

import java.util.Map;

import com.atrapfare.dashboard.core.CollectorRegistry;
import com.atrapfare.dashboard.core.Payload;
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
}
