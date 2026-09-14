package com.atrapfare.dashboard.sources.news;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.atrapfare.dashboard.config.DashboardProperties;
import com.atrapfare.dashboard.core.Collector;
import com.atrapfare.dashboard.core.CollectorRegistry;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NewsCollector implements Collector<NewsData> {

	private static final Logger log = LoggerFactory.getLogger(NewsCollector.class);

	private final DashboardProperties.News properties;

	private final RestClient restClient;

	public NewsCollector(DashboardProperties properties, RestClient.Builder restClientBuilder) {
		this.properties = properties.news();
		this.restClient = restClientBuilder.build();
	}

	@Override
	public String id() {
		return "news";
	}

	@Override
	public Duration interval() {
		return properties.interval();
	}

	@Override
	public NewsData collect() {
		List<DashboardProperties.News.Feed> feeds = properties.feeds();
		if (feeds == null || feeds.isEmpty()) {
			return new NewsData(List.of(), List.of());
		}

		List<FeedResult> results;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<CompletableFuture<FeedResult>> futures = feeds.stream()
					.map(feed -> CompletableFuture.supplyAsync(() -> fetch(feed), executor))
					.toList();
			results = futures.stream().map(CompletableFuture::join).toList();
		}

		// Ein ausgefallener Feed darf die uebrigen nicht entwerten - das
		// Ergebnis ist die Vereinigung der erfolgreichen, der Rest wird im
		// Payload vermerkt statt geworfen.
		List<NewsData.FeedFailure> failures = results.stream()
				.filter(result -> result.error() != null)
				.map(result -> new NewsData.FeedFailure(result.name(), result.error()))
				.toList();

		Map<String, NewsItem> byLink = new LinkedHashMap<>();
		for (FeedResult result : results) {
			for (NewsItem item : result.items()) {
				byLink.putIfAbsent(item.link(), item);
			}
		}

		List<NewsItem> items = new ArrayList<>(byLink.values());
		// Eintraege ohne Datum ans Ende, der Rest absteigend nach Zeitpunkt.
		items.sort(Comparator.comparing(NewsItem::publishedAt,
				Comparator.nullsLast(Comparator.reverseOrder())));

		if (items.size() > properties.maxItems()) {
			items = items.subList(0, properties.maxItems());
		}

		if (failures.size() == results.size()) {
			throw new IllegalStateException("Kein Feed erreichbar: " + failures.stream()
					.map(failure -> failure.name() + " (" + failure.error() + ")")
					.toList());
		}

		return new NewsData(List.copyOf(items), failures);
	}

	private FeedResult fetch(DashboardProperties.News.Feed feed) {
		try {
			byte[] body = restClient.get().uri(feed.url()).retrieve().body(byte[].class);
			if (body == null || body.length == 0) {
				return new FeedResult(feed.name(), List.of(), "leere Antwort");
			}

			SyndFeed parsed;
			try (XmlReader reader = new XmlReader(new ByteArrayInputStream(body))) {
				parsed = new SyndFeedInput().build(reader);
			}

			List<NewsItem> items = parsed.getEntries().stream()
					.map(entry -> toItem(entry, feed.name()))
					.filter(item -> item.link() != null && !item.link().isBlank())
					.toList();
			return new FeedResult(feed.name(), items, null);
		}
		catch (Exception e) {
			log.warn("Feed '{}' fehlgeschlagen: {}", feed.name(), e.toString());
			String message = (e.getMessage() == null || e.getMessage().isBlank())
					? e.getClass().getSimpleName() : CollectorRegistry.shorten(e.getMessage());
			return new FeedResult(feed.name(), List.of(), message);
		}
	}

	/**
	 * Rome normalisiert RFC-822 und ISO-8601 bereits selbst. Uebrig bleibt der
	 * Fall, dass ein Feed gar kein Datum liefert - dann bleibt das Feld leer
	 * und der Eintrag sortiert ans Ende.
	 */
	private static NewsItem toItem(SyndEntry entry, String source) {
		Date published = (entry.getPublishedDate() != null) ? entry.getPublishedDate() : entry.getUpdatedDate();
		String title = (entry.getTitle() == null) ? "(ohne Titel)" : entry.getTitle().strip();
		return new NewsItem(
				title,
				entry.getLink(),
				source,
				(published == null) ? null : published.toInstant());
	}

	private record FeedResult(String name, List<NewsItem> items, String error) {
	}
}
