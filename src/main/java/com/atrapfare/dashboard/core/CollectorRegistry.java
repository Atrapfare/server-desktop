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
import java.util.regex.Pattern;

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
	 * Wartezeit bis zum zweiten Anlauf nach einem Fehlschlag. Kurz genug, dass
	 * die Kachel nicht lange leer bleibt, lang genug, dass eine kurze Stoerung
	 * inzwischen vorbei sein kann.
	 */
	private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

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
		refresh(collector, true);
	}

	private void refresh(Collector<?> collector, boolean mayRetry) {
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
			if (mayRetry && shouldRetry(collector)) {
				scheduleRetry(collector);
			}
		}
		publisher.publish(payloads.get(id));
	}

	/**
	 * Auf einer unzuverlaessigen Leitung scheitert ein Abruf schon mal, obwohl
	 * die Gegenstelle laeuft. Bei einem langen Intervall stuende die Kachel dann
	 * bis zum naechsten regulaeren Versuch auf veraltet - bei den Nachrichten
	 * eine ganze Stunde. Ein kurzer zweiter Anlauf holt das auf.
	 *
	 * Nur bei Intervallen, die deutlich ueber der Wartezeit liegen: kommt der
	 * regulaere Versuch ohnehin gleich, waere die Wiederholung nur zusaetzliche
	 * Last auf einer Leitung, die gerade Muehe hat.
	 */
	private boolean shouldRetry(Collector<?> collector) {
		return collector.interval().compareTo(RETRY_DELAY.multipliedBy(3)) > 0;
	}

	/**
	 * Genau ein Versuch, keine Kette: scheitert auch er, bleibt es beim
	 * regulaeren Zeitplan.
	 */
	private void scheduleRetry(Collector<?> collector) {
		try {
			scheduler.schedule(() -> refresh(collector, false), Instant.now().plus(RETRY_DELAY));
			log.debug("Collector '{}' wird in {} s erneut versucht", collector.id(), RETRY_DELAY.toSeconds());
		}
		catch (Exception e) {
			// Beim Herunterfahren nimmt der Scheduler nichts mehr an. Kein Grund,
			// den laufenden Abruf daran scheitern zu lassen.
			log.debug("Wiederholung fuer '{}' nicht mehr eingeplant: {}", collector.id(), e.toString());
		}
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

	/**
	 * Adressen in Fehlermeldungen werden auf den Rechnernamen gekuerzt.
	 *
	 * Der Pfad einer Adresse ist manchmal das Geheimnis selbst - die private
	 * iCal-Adresse eines Kalenders etwa gibt jedem Lesezugriff, der sie kennt.
	 * Fehlermeldungen stehen aber offen auf dem Dashboard und gehen ueber SSE
	 * an jeden verbundenen Client. Der Rechnername reicht zur Eingrenzung.
	 */
	private static final Pattern URL = Pattern.compile("(https?://[^/\s\"]+)[^\s\"]*");

	public static String shorten(String message) {
		String collapsed = URL.matcher(message.replaceAll("\s+", " ").strip())
				.replaceAll("$1/…");
		return (collapsed.length() <= MAX_ERROR_LENGTH)
				? collapsed : collapsed.substring(0, MAX_ERROR_LENGTH) + " …";
	}
}
