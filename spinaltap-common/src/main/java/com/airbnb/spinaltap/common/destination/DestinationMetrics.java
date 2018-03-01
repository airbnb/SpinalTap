/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.metrics.SpinalTapMetrics;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DestinationMetrics extends SpinalTapMetrics {
  private static final String DESTINATION_PREFIX = METRIC_PREFIX + ".destination";

  private static final String START_METRIC = DESTINATION_PREFIX + ".start.count";
  private static final String START_FAILURE_METRIC = DESTINATION_PREFIX + ".start.failure.count";

  private static final String PUBLISH_METRIC = DESTINATION_PREFIX + ".publish.success.count";
  private static final String PUBLISH_FAILURE_METRIC =
      DESTINATION_PREFIX + ".publish.failure.count";
  private static final String PUBLISH_BATCH_SIZE_METRIC =
      DESTINATION_PREFIX + ".publish.batch.size";

  private static final String PUBLISH_LAG_METRIC = DESTINATION_PREFIX + ".publish.lag";
  private static final String PUBLISH_LAG_GAUGE_METRIC = DESTINATION_PREFIX + ".publish.lag.gauge";
  private static final String PUBLISH_TIME_METRIC = DESTINATION_PREFIX + ".publish.time";

  private static final String PUBLISH_OUT_OF_ORDER_METRIC =
      DESTINATION_PREFIX + ".publish.out.of.order.count";

  private static final String SEND_TIME_METRIC = DESTINATION_PREFIX + ".send.time";
  private static final String SEND_FAILURE_METRIC = DESTINATION_PREFIX + ".send.failure.count";

  private static final String BUFFER_SIZE_METRIC = DESTINATION_PREFIX + ".buffer.size";
  private static final String BUFFER_FULL_METRIC = DESTINATION_PREFIX + ".buffer.full";

  private static final String OUT_OF_ORDER_METRIC =
      DESTINATION_PREFIX + ".mutation.out_of_order.count";

  private final AtomicReference<Long> mutationLag = new AtomicReference<>();

  public DestinationMetrics(
      String sourceName, String sourceType, TaggedMetricRegistry metricRegistry) {
    this(sourceName, sourceType, ImmutableMap.of(), metricRegistry);
  }

  public DestinationMetrics(
      String sourceName,
      String sourceType,
      Map<String, String> tags,
      TaggedMetricRegistry metricRegistry) {
    this(
        ImmutableMap.<String, String>builder()
            .put(SOURCE_NAME_TAG, sourceName)
            .put(SOURCE_TYPE_TAG, sourceType)
            .putAll(tags)
            .build(),
        metricRegistry);
  }

  public DestinationMetrics(
      ImmutableMap<String, String> tags, TaggedMetricRegistry metricRegistry) {
    super(tags, metricRegistry);

    registerGauge(PUBLISH_LAG_GAUGE_METRIC, mutationLag::get);
  }

  public void start() {
    inc(START_METRIC);
  }

  public void startFailure(Throwable error) {
    incError(START_FAILURE_METRIC, error);
  }

  public void publishSucceeded(List<? extends Mutation<?>> mutations) {
    long lastTimestamp =
        mutations
            .stream()
            .map(mutation -> mutation.getMetadata().getTimestamp())
            .max(Long::compare)
            .orElse(0L);

    mutationLag.set(System.currentTimeMillis() - lastTimestamp);
    update(PUBLISH_BATCH_SIZE_METRIC, mutations.size());

    mutations.forEach(
        mutation -> {
          long lag = System.currentTimeMillis() - mutation.getMetadata().getTimestamp();
          Map<String, String> mutationTags = getTags(mutation);
          update(PUBLISH_LAG_METRIC, lag, mutationTags);
          inc(PUBLISH_METRIC, mutationTags);
        });
  }

  public void publishFailed(Mutation<?> mutation, Throwable error) {
    incError(PUBLISH_FAILURE_METRIC, error, getTags(mutation));
  }

  public void publishOutOfOrder(String tpName) {
    Map<String, String> topicTags = Maps.newHashMap();

    topicTags.put(TOPIC_NAME_TAG, tpName);
    inc(PUBLISH_OUT_OF_ORDER_METRIC, topicTags);
  }

  public void publishTime(long timeInMilliseconds) {
    update(PUBLISH_TIME_METRIC, timeInMilliseconds);
  }

  public void sendFailed(Throwable error) {
    incError(SEND_FAILURE_METRIC, error);
  }

  public void sendTime(long delayInMilliseconds) {
    update(SEND_TIME_METRIC, delayInMilliseconds);
  }

  public void bufferSize(int size, Mutation.Metadata metadata) {
    update(BUFFER_SIZE_METRIC, size, getTags(metadata));
  }

  public void bufferFull(Mutation.Metadata metadata) {
    inc(BUFFER_FULL_METRIC, getTags(metadata));
  }

  public void outOfOrder(Mutation<?> mutation) {
    inc(OUT_OF_ORDER_METRIC, getTags(mutation));
  }

  @Override
  public void clear() {
    removeGauge(PUBLISH_LAG_GAUGE_METRIC);
    mutationLag.set(null);
  }
}
