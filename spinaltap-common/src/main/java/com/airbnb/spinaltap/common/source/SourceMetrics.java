/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.metrics.SpinalTapMetrics;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Responsible for metrics collection for a {@link Source}. */
public class SourceMetrics extends SpinalTapMetrics {
  protected static final String SOURCE_PREFIX = METRIC_PREFIX + ".source";

  private static final String START_METRIC = SOURCE_PREFIX + ".start.count";
  private static final String STOP_METRIC = SOURCE_PREFIX + ".stop.count";
  private static final String CHECKPOINT_METRIC = SOURCE_PREFIX + ".checkpoint.count";

  private static final String START_FAILURE_METRIC = SOURCE_PREFIX + ".start.failure.count";
  private static final String STOP_FAILURE_METRIC = SOURCE_PREFIX + ".stop.failure.count";
  private static final String CHECKPOINT_FAILURE_METRIC =
      SOURCE_PREFIX + ".checkpoint.failure.count";

  private static final String EVENT_COUNT_METRIC = SOURCE_PREFIX + ".event.count";
  private static final String EVENT_FAILURE_METRIC = SOURCE_PREFIX + ".event.failure.count";

  private static final String EVENT_LAG_METRIC = SOURCE_PREFIX + ".event.lag";
  private static final String EVENT_LAG_GAUGE_METRIC = SOURCE_PREFIX + ".event.lag.gauge";
  private static final String EVENT_PROCESS_TIME_METRIC = SOURCE_PREFIX + ".event.process.time";

  private static final String EVENT_OUT_OF_ORDER_METRIC =
      SOURCE_PREFIX + ".event.out_of_order.count";
  private static final String MUTATION_OUT_OF_ORDER_METRIC =
      SOURCE_PREFIX + ".mutation.out_of_order.count";

  private final AtomicReference<Long> eventLag = new AtomicReference<>();

  public SourceMetrics(
      String sourceName, String sourceType, TaggedMetricRegistry taggedMetricRegistry) {
    this(sourceName, sourceType, ImmutableMap.of(), taggedMetricRegistry);
  }

  public SourceMetrics(
      String sourceName,
      String sourceType,
      Map<String, String> tags,
      TaggedMetricRegistry metricRegistry) {
    super(
        ImmutableMap.<String, String>builder()
            .put(SOURCE_TYPE_TAG, sourceType)
            .put(SOURCE_NAME_TAG, sourceName)
            .putAll(tags)
            .build(),
        metricRegistry);

    registerGauge(EVENT_LAG_GAUGE_METRIC, eventLag::get);
  }

  public void start() {
    inc(START_METRIC);
  }

  public void stop() {
    inc(STOP_METRIC);
  }

  public void checkpoint() {
    inc(CHECKPOINT_METRIC);
  }

  public void startFailure(Throwable error) {
    incError(START_FAILURE_METRIC, error);
  }

  public void stopFailure(Throwable error) {
    incError(STOP_FAILURE_METRIC, error);
  }

  public void checkpointFailure(Throwable error) {
    incError(CHECKPOINT_FAILURE_METRIC, error);
  }

  public void eventFailure(Throwable error) {
    incError(EVENT_FAILURE_METRIC, error);
  }

  public void eventReceived(SourceEvent event) {
    long lag = System.currentTimeMillis() - event.getTimestamp();
    Map<String, String> eventTags = getTags(event);

    inc(EVENT_COUNT_METRIC, eventTags, event.size());
    update(EVENT_LAG_METRIC, lag, eventTags);

    eventLag.set(lag);
  }

  public void processEventTime(SourceEvent sourceEvent, long timeInMilliseconds) {
    update(EVENT_PROCESS_TIME_METRIC, timeInMilliseconds, getTags(sourceEvent));
  }

  public void outOfOrder(SourceEvent event) {
    inc(EVENT_OUT_OF_ORDER_METRIC, getTags(event));
  }

  public void outOfOrder(Mutation<?> mutation) {
    inc(MUTATION_OUT_OF_ORDER_METRIC, getTags(mutation));
  }

  @Override
  public void clear() {
    removeGauge(EVENT_LAG_GAUGE_METRIC);
    eventLag.set(null);
  }
}
