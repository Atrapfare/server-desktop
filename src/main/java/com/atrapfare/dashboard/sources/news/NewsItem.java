package com.atrapfare.dashboard.sources.news;

import java.time.Instant;

public record NewsItem(
		String title,
		String link,
		String source,
		Instant publishedAt
) {
}
