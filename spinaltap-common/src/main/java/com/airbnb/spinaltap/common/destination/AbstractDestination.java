/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.DestinationException;
import com.airbnb.spinaltap.common.util.BatchMapper;
import com.google.common.base.Stopwatch;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDestination<T> extends ListenableDestination {
  @NonNull private final BatchMapper<Mutation<?>, T> mapper;
  @NonNull private final DestinationMetrics metrics;
  private final long delaySendMs;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicReference<Mutation<?>> lastPublishedMutation = new AtomicReference<>();

  @Override
  public Mutation<?> getLastPublishedMutation() {
    return lastPublishedMutation.get();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void send(@NonNull final List<? extends Mutation<?>> mutations) {
    if (mutations.isEmpty()) {
      return;
    }

    try {
      final Stopwatch stopwatch = Stopwatch.createStarted();

      // introduce delay before mapper apply
      final Mutation<?> latestMutation = mutations.get(mutations.size() - 1);
      delay(latestMutation);

      final List<T> messages = mapper.apply(mutations.stream().collect(Collectors.toList()));
      publish(messages);

      lastPublishedMutation.set(latestMutation);

      stopwatch.stop();
      final long time = stopwatch.elapsed(TimeUnit.MILLISECONDS);

      metrics.publishTime(time);
      metrics.publishSucceeded(mutations);

      log(mutations);
      notifySend(mutations);

    } catch (Exception ex) {
      log.error("Failed to send {} mutations.", mutations.size(), ex);
      mutations.forEach(mutation -> metrics.publishFailed(mutation, ex));

      throw new DestinationException("Failed to send mutations", ex);
    }
  }

  /**
   * Induces a delay given the configured delay time.
   *
   * @param mutation The {@link Mutation} for which to consider the delay
   * @throws InterruptedException
   */
  private void delay(final Mutation<?> mutation) throws InterruptedException {
    final long delayMs = System.currentTimeMillis() - mutation.getMetadata().getTimestamp();
    if (delayMs >= delaySendMs) {
      return;
    }

    Thread.sleep(delaySendMs - delayMs);
  }

  public abstract void publish(List<T> messages) throws Exception;

  private void log(final List<? extends Mutation<?>> mutations) {
    mutations.forEach(
        mutation ->
            log.trace(
                "Sent {} mutations with metadata {}.", mutation.getType(), mutation.getMetadata()));
  }

  @Override
  public boolean isStarted() {
    return started.get();
  }

  @Override
  public void open() {
    lastPublishedMutation.set(null);
    super.open();

    started.set(true);
  }

  @Override
  public void close() {
    started.set(false);
  }

  @Override
  public void clear() {
    metrics.clear();
  }
}
