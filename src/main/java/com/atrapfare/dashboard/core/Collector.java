package com.atrapfare.dashboard.core;

import java.time.Duration;

public interface Collector<T> {

	String id();

	Duration interval();

	T collect() throws Exception;
}
