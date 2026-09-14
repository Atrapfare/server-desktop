package com.atrapfare.dashboard.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class CollectorRegistry {

	private static final Logger log = LoggerFactory.getLogger(CollectorRegistry.class);

	/**
	 * Muss ueber der Summe aus Verbindungs- und Lesezeit der HTTP-Schicht
	 * liegen, sonst wird ein Abruf hier abgebrochen, bevor er dort mit einer
	 * sprechenden Meldung scheitert.
	 */
	private static final Duration COLLECT_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * Fehlermeldungen landen im Payload und gehen ueber SSE an jeden Client.
	 * Ein HTTP-Fehler traegt schon mal eine komplette HTML-Seite im Text -
	 * die gehoert nicht ins Dashboard.
	 */
	private static final int MAX_ERROR_LENGTH = 200;

	private final List<Collector<?>> collectors;

	private final DashboardEventPublisher publisher;

	private final Map<String, Payload<?>> payloads = new ConcurrentHashMap<>();

	private final ThreadPoolTaskScheduler scheduler;

	/**
	 * Der eigentliche collect()-Aufruf laeuft nicht auf dem Scheduler-Thread,
	 * sondern hier. Nur so laesst sich ein haengender HTTP-Aufruf nach
	 * COLLECT_TIMEOUT aufgeben, ohne dass der Zeitplan der uebrigen Collector
	 * stehen bleibt.
	 */
	private final ExecutorService collectExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public CollectorRegistry(List<Collector<?>> collectors, DashboardEventPublisher publisher) {
		this.collectors = List.copyOf(collectors);
		this.publisher = publisher;
		this.scheduler = new ThreadPoolTaskScheduler();
		this.scheduler.setPoolSize(Math.max(1, collectors.size()));
		this.scheduler.setThreadNamePrefix("collector-");
		this.scheduler.setWaitForTasksToCompleteOnShutdown(false);
	}

	@PostConstruct
	void start() {
		scheduler.initialize();
		for (Collector<?> collector : collectors) {
			log.info("Collector '{}' registriert, Intervall {}", collector.id(), collector.interval());
			// scheduleAtFixedRate laeuft sofort das erste Mal - das ist der
			// geforderte asynchrone Erstabruf, ohne den Start zu blockieren.
			scheduler.scheduleAtFixedRate(() -> refresh(collector), collector.interval());
		}
	}

	@PreDestroy
	void stop() {
		scheduler.shutdown();
		collectExecutor.shutdownNow();
	}

	public Map<String, Payload<?>> payloads() {
		return Map.copyOf(payloads);
	}

	public List<Collector<?>> collectors() {
		return collectors;
	}

	public void refresh(Collector<?> collector) {
		String id = collector.id();
		Future<?> task = collectExecutor.submit(collector::collect);
		try {
			Object data = task.get(COLLECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			payloads.put(id, Payload.ok(id, data, Instant.now()));
			log.debug("Collector '{}' aktualisiert", id);
		}
		catch (Exception e) {
			task.cancel(true);
			payloads.put(id, degrade(id, e));
			log.warn("Collector '{}' fehlgeschlagen: {}", id, describe(e));
		}
		publisher.publish(payloads.get(id));
	}

	/**
	 * Ein Fehler entwertet einen frueher geholten Wert nicht - er wird nur
	 * veraltet. Erst wenn nie ein Abruf gelang, gibt es nichts anzuzeigen und
	 * der Zustand ist ERROR.
	 */
	private Payload<?> degrade(String id, Exception cause) {
		String message = describe(cause);
		Payload<?> previous = payloads.get(id);
		if (previous == null || previous.status() == Status.ERROR) {
			return Payload.error(id, message);
		}
		return previous.asStale(message);
	}

	private String describe(Exception e) {
		Throwable cause = (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null)
				? e.getCause() : e;
		if (cause instanceof TimeoutException) {
			return "Zeitueberschreitung nach " + COLLECT_TIMEOUT.toSeconds() + " s";
		}
		String message = cause.getMessage();
		String text = (message == null || message.isBlank())
				? cause.getClass().getSimpleName()
				: cause.getClass().getSimpleName() + ": " + shorten(message);

		// Netzwerkfehler kommen verpackt an: die aeussere Meldung nennt nur die
		// Adresse und haengt die des Ausloesers an - die ist oft leer ("... :
		// null"). Dann steht in der Kachel nichts Brauchbares. Also die
		// urspruengliche Ursache dazusagen, sie benennt das eigentliche Problem.
		Throwable root = rootCause(cause);
		if (root == cause) {
			return text;
		}
		String rootMessage = root.getMessage();
		String rootText = (rootMessage == null || rootMessage.isBlank())
				? root.getClass().getSimpleName()
				: root.getClass().getSimpleName() + ": " + shorten(rootMessage);
		return text.contains(rootText) ? text : text + " [" + rootText + "]";
	}

	private static Throwable rootCause(Throwable throwable) {
		Throwable current = throwable;
		// Zyklen sind in Ausnahmeketten selten, aber moeglich; der Zaehler
		// verhindert, dass die Schleife daran haengen bleibt.
		for (int depth = 0; depth < 10 && current.getCause() != null && current.getCause() != current; depth++) {
			current = current.getCause();
		}
		return current;
	}

	public static String shorten(String message) {
		String collapsed = message.replaceAll("\s+", " ").strip();
		return (collapsed.length() <= MAX_ERROR_LENGTH)
				? collapsed : collapsed.substring(0, MAX_ERROR_LENGTH) + " …";
	}
}
