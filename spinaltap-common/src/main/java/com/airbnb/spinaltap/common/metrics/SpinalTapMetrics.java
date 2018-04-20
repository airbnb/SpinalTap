/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.metrics;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.source.SourceEvent;
import com.codahale.metrics.Gauge;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** Base class for metrics collection. */
@RequiredArgsConstructor
public abstract class SpinalTapMetrics {
  protected static final String METRIC_PREFIX = "spinaltap";

  protected static final String ERROR_TAG = "error";
  protected static final String EVENT_TYPE_TAG = "event_type";
  protected static final String MUTATION_TYPE_TAG = "mutation_type";

  protected static final String HOST_NAME_TAG = "host_name";
  protected static final String SERVER_NAME_TAG = "server_name";

  public static final String DATABASE_NAME_TAG = "database_name";
  public static final String TABLE_NAME_TAG = "table_name";
  public static final String TOPIC_NAME_TAG = "topic_name";

  public static final String RESOURCE_NAME_TAG = "resource_name";
  public static final String INSTANCE_NAME_TAG = "instance_name";

  public static final String DATA_STORE_TYPE_TAG = "data_store_type";
  public static final String SOURCE_TYPE_TAG = "source_type";
  public static final String SOURCE_NAME_TAG = "source_name";

  private final ImmutableMap<String, String> defaultTags;
  private final TaggedMetricRegistry metricRegistry;

  public SpinalTapMetrics(TaggedMetricRegistry metricRegistry) {
    this(ImmutableMap.of(), metricRegistry);
  }

  protected void registerGauge(String metricName, Gauge<?> gauge) {
    registerGauge(metricName, gauge, Collections.emptyMap());
  }

  protected void registerGauge(String metricName, Gauge<?> gauge, Map<String, String> tags) {
    // Remove the old gauge from the registry if it exists, since we
    // cannot register a new gauge if the key already is present
    removeGauge(metricName, tags);

    Map<String, String> allTags = new HashMap<>();

    allTags.putAll(defaultTags);
    allTags.putAll(tags);

    metricRegistry.<Gauge<?>>register(metricName, allTags, gauge);
  }

  protected void removeGauge(String metricName) {
    removeGauge(metricName, Collections.emptyMap());
  }

  protected void removeGauge(String metricName, Map<String, String> tags) {
    Map<String, String> allTags = new HashMap<>();

    allTags.putAll(defaultTags);
    allTags.putAll(tags);

    metricRegistry.remove(metricName, allTags);
  }

  protected void incError(String metricName, Throwable error) {
    incError(metricName, error, Collections.emptyMap());
  }

  protected void incError(String metricName, Throwable error, Map<String, String> tags) {
    Map<String, String> errorTags = Maps.newHashMap();

    errorTags.put(ERROR_TAG, error.getClass().toString());
    errorTags.putAll(tags);

    inc(metricName, errorTags);
  }

  protected void inc(String metricName) {
    inc(metricName, Collections.emptyMap());
  }

  protected void inc(String metricName, Map<String, String> tags) {
    inc(metricName, tags, 1);
  }

  protected void inc(String metricName, Map<String, String> tags, int count) {
    Map<String, String> allTags = Maps.newHashMap();

    allTags.putAll(defaultTags);
    allTags.putAll(tags);

    metricRegistry.counter(metricName, allTags).inc(count);
  }

  protected void update(String metricName, long value) {
    update(metricName, value, Collections.emptyMap());
  }

  protected void update(String metricName, long value, Map<String, String> tags) {
    Map<String, String> allTags = Maps.newHashMap();

    allTags.putAll(defaultTags);
    allTags.putAll(tags);

    metricRegistry.histogram(metricName, allTags).update(value);
  }

  protected Map<String, String> getTags(SourceEvent event) {
    Map<String, String> eventTags = new HashMap<>();

    eventTags.putAll(defaultTags);
    eventTags.put(EVENT_TYPE_TAG, event.getClass().getSimpleName());

    return eventTags;
  }

  protected Map<String, String> getTags(Mutation<?> mutation) {
    Map<String, String> mutationTags = Maps.newHashMap();

    mutationTags.putAll(getTags(mutation.getMetadata()));
    mutationTags.put(MUTATION_TYPE_TAG, mutation.getType().toString());

    return mutationTags;
  }

  protected Map<String, String> getTags(Mutation.Metadata metadata) {
    return Collections.emptyMap();
  }

  public void clear() {}
}
