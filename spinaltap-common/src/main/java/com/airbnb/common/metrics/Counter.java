package com.airbnb.common.metrics;

/**
 * A monotone counter
 */
public interface Counter {
  /** Increment the counter by one. */
  public void inc();

  /**
   * Increment the counter by {@code n}.
   *
   * @param n the amount by which the counter will be increased
   */
  public void inc(long n);
}
