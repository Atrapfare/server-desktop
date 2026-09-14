package com.atrapfare.dashboard.sources.news;

import java.util.List;

public record NewsData(
		List<NewsItem> items,
		List<FeedFailure> failedFeeds
) {

	public record FeedFailure(String name, String error) {
	}
}
