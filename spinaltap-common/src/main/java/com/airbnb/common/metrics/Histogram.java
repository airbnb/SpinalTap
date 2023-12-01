/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

/** A Histogram interface used to record and analyze the distribution of values. */
public interface Histogram {
  /**
   * Adds a recorded value.
   *
   * @param value the length of the value
   */
  public void update(long value);
}
