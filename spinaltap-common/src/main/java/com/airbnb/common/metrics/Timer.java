/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

import com.codahale.metrics.Clock;
import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Our own version of the Timer class that multiplexes tagged metrics to tagged and non-tagged
 * versions, because Datadog is not capable of averaging across tag values correctly.
 */
public class Timer {

  private final Clock clock;
  private final com.codahale.metrics.Timer[] timers;

  public Timer(Clock clock, com.codahale.metrics.Timer... timers) {
    this.timers = timers;
    this.clock = clock;
  }

  public Timer(com.codahale.metrics.Timer... timers) {
    this(Clock.defaultClock(), timers);
  }

  /**
   * Adds a recorded duration.
   *
   * @param duration the length of the duration
   * @param unit the scale unit of {@code duration}
   */
  public void update(long duration, TimeUnit unit) {
    for (com.codahale.metrics.Timer timer : timers) {
      timer.update(duration, unit);
    }
  }

  /**
   * Times and records the duration of event.
   *
   * @param event a {@link Callable} whose {@link Callable#call()} method implements a process whose
   *     duration should be timed
   * @param <T> the type of the value returned by {@code event}
   * @return the value returned by {@code event}
   * @throws Exception if {@code event} throws an {@link Exception}
   */
  public <T> T time(Callable<T> event) throws Exception {
    final long startTime = clock.getTick();
    try {
      return event.call();
    } finally {
      update(clock.getTick() - startTime, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Returns a new {@link Context}.
   *
   * @return a new {@link Context}
   * @see Context
   */
  public Context time() {
    com.codahale.metrics.Timer.Context[] contexts =
        new com.codahale.metrics.Timer.Context[timers.length];
    for (int i = 0; i < timers.length; i++) {
      contexts[i] = timers[i].time();
    }
    return new Context(contexts);
  }

  /**
   * A timing context.
   *
   * @see Timer#time()
   */
  public static class Context implements Closeable {

    private com.codahale.metrics.Timer.Context[] contexts;

    private Context(com.codahale.metrics.Timer.Context... contexts) {
      this.contexts = contexts;
    }

    /**
     * Stops recording the elapsed time, updates the timer and returns the elapsed time in
     * nanoseconds.
     */
    public long stop() {
      long elapsed = 0;
      for (com.codahale.metrics.Timer.Context context : contexts) {
        elapsed = context.stop();
      }
      return elapsed;
    }

    @Override
    public void close() {
      stop();
    }
  }
}
