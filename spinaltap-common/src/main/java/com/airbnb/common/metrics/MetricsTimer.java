/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

import com.google.common.base.Stopwatch;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import lombok.Builder;

@Builder
public class MetricsTimer implements Closeable {

  private final String metricName;
  private final TimeUnit timeUnit;
  private final Stopwatch stopwatch;
  private final String[] tags;
  private final TaggedMetricRegistry taggedMetricRegistry;

  public static class MetricsTimerBuilder {

    public MetricsTimerBuilder tags(String... tags) {
      this.tags = tags;
      return this;
    }

    public MetricsTimer build() {
      if (timeUnit == null) {
        timeUnit = TimeUnit.MILLISECONDS;
      }
      if (stopwatch == null) {
        stopwatch = Stopwatch.createStarted();
      }
      if (taggedMetricRegistry == null) {
        taggedMetricRegistry = TaggedMetricRegistryFactory.get();
      }
      return new MetricsTimer(metricName, timeUnit, stopwatch, tags, taggedMetricRegistry);
    }
  }

  @Override
  public void close() {
    done();
  }

  public long done() {
    long requestTime = stopwatch.elapsed(timeUnit);
    taggedMetricRegistry.histogram(metricName, tags).update(requestTime);
    return requestTime;
  }

  /**
   * Does not close or record a metric; merely checks the time elapsed since object creation.
   *
   * @return the elapsed time in specified time unit
   */
  public long check() {
    return stopwatch.elapsed(timeUnit);
  }
}
