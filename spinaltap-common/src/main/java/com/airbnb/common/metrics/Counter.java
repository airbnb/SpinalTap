/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

/**
 * Our own version of the Counter class that multiplexes tagged metrics to tagged and non-tagged
 * versions, because Datadog is not capable of averaging across tag values correctly.
 */
public class Counter {

  private final com.codahale.metrics.Counter[] counters;

  public Counter(com.codahale.metrics.Counter... counters) {
    this.counters = counters;
  }

  /** Increment the counter by one. */
  public void inc() {
    inc(1);
  }

  /**
   * Increment the counter by {@code n}.
   *
   * @param n the amount by which the counter will be increased
   */
  public void inc(long n) {
    for (com.codahale.metrics.Counter counter : counters) {
      counter.inc(n);
    }
  }

  /** Decrement the counter by one. */
  public void dec() {
    dec(1);
  }

  /**
   * Decrement the counter by {@code n}.
   *
   * @param n the amount by which the counter will be decreased
   */
  public void dec(long n) {
    for (com.codahale.metrics.Counter counter : counters) {
      counter.dec(n);
    }
  }
}
