/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.pipe;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.common.metrics.SpinalTapMetrics;
import com.google.common.collect.ImmutableMap;

/** Responsible for metrics collection for a {@link Pipe}. */
public class PipeMetrics extends SpinalTapMetrics {
  private static final String PIPE_PREFIX = METRIC_PREFIX + ".pipe";

  private static final String OPEN_METRIC = PIPE_PREFIX + ".open.count";
  private static final String CLOSE_METRIC = PIPE_PREFIX + ".close.count";
  private static final String START_METRIC = PIPE_PREFIX + ".start.count";
  private static final String STOP_METRIC = PIPE_PREFIX + ".stop.count";
  private static final String CHECKPOINT_METRIC = PIPE_PREFIX + ".checkpoint.count";

  public PipeMetrics(String sourceName, TaggedMetricRegistry metricRegistry) {
    this(ImmutableMap.of(SOURCE_NAME_TAG, sourceName), metricRegistry);
  }

  public PipeMetrics(ImmutableMap<String, String> tags, TaggedMetricRegistry metricRegistry) {
    super(tags, metricRegistry);
  }

  public void open() {
    inc(OPEN_METRIC);
  }

  public void close() {
    inc(CLOSE_METRIC);
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
}
