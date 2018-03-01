/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.DestinationException;
import com.airbnb.spinaltap.common.util.BatchMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDestination<T> extends ListenableDestination {
  private final BatchMapper<Mutation<?>, T> mapper;
  private final DestinationMetrics metrics;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final AtomicReference<Mutation<?>> lastPublishedMutation = new AtomicReference<>();

  public Mutation<?> getLastPublishedMutation() {
    return lastPublishedMutation.get();
  }

  public void send(List<? extends Mutation<?>> mutations) {
    if (mutations.isEmpty()) {
      return;
    }

    try {
      long start = System.currentTimeMillis();

      List<T> messages = mapper.apply(mutations.stream().collect(Collectors.toList()));

      publish(messages);

      lastPublishedMutation.set(mutations.get(mutations.size() - 1));

      long end = System.currentTimeMillis();

      metrics.publishTime(end - start);
      metrics.publishSucceeded(mutations);

      mutations.forEach(
          mutation ->
              log.trace(
                  "Sent {} mutation with metadata {}", mutation.getType(), mutation.getMetadata()));

      notifySend(mutations);
    } catch (Exception ex) {
      log.error("Failed to send mutations", ex);
      mutations.forEach(mutation -> metrics.publishFailed(mutation, ex));

      throw new DestinationException("Failed to send mutations", ex);
    }
  }

  public abstract void publish(List<T> messages) throws Exception;

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
