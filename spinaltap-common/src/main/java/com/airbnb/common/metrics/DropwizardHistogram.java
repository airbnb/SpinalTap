/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

/**
 * Our own version of the Histogram class that multiplexes tagged metrics to tagged and non-tagged
 * versions, because Datadog is not capable of averaging across tag values correctly.
 */
public class DropwizardHistogram implements Histogram {

  private final com.codahale.metrics.Histogram[] histograms;

  public DropwizardHistogram(com.codahale.metrics.Histogram... histograms) {
    this.histograms = histograms;
  }

  /**
   * Adds a recorded value.
   *
   * @param value the length of the value
   */
  public void update(int value) {
    update((long) value);
  }

  /**
   * Adds a recorded value.
   *
   * @param value the length of the value
   */
  public void update(long value) {
    for (com.codahale.metrics.Histogram histogram : histograms) {
      histogram.update(value);
    }
  }
}
