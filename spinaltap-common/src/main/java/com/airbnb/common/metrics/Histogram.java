package com.airbnb.common.metrics;

/**
 * A Histogram interface used to record and analyze the distribution of values.
 */
public interface Histogram {
  /**
   * Adds a recorded value.
   *
   * @param value the length of the value
   */
  public void update(long value);
}
