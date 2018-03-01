/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

/**
 * Our own version of the Counter class that multiplexes tagged metrics to tagged and non-tagged
 * versions, because Datadog is not capable of averaging across tag values correctly.
 */
public class Meter {

  private final com.codahale.metrics.Meter[] meters;

  /** Creates a new {@link Meter}. */
  public Meter(com.codahale.metrics.Meter... meters) {
    this.meters = meters;
  }

  /** Mark the occurrence of an event. */
  public void mark() {
    for (com.codahale.metrics.Meter meter : meters) {
      meter.mark();
    }
  }

  /**
   * Mark the occurrence of a given number of events.
   *
   * @param n the number of events
   */
  public void mark(long n) {
    for (com.codahale.metrics.Meter meter : meters) {
      meter.mark(n);
    }
  }
}
