/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.Filter;
import com.airbnb.spinaltap.common.util.Mapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Base implementation for Data Store source (Such as MySQL, DynamoDB).
 *
 * @param <E> The event type produced by the source
 */
@Slf4j
public abstract class AbstractDataStoreSource<E extends SourceEvent> extends AbstractSource<E> {
  private @Nullable ExecutorService processor;

  public AbstractDataStoreSource(
      String name,
      SourceMetrics metrics,
      Mapper<E, List<? extends Mutation<?>>> mutationMapper,
      Filter<E> eventFilter) {
    super(name, metrics, mutationMapper, eventFilter);
  }

  @Override
  protected synchronized void start() {
    processor =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(name + "-source-processor").build());

    processor.execute(
        () -> {
          try {
            connect();
          } catch (Exception ex) {
            started.set(false);
            metrics.startFailure(ex);
            log.error("Failed to stream events for source " + name, ex);
          }
        });
  }

  @Override
  protected void stop() throws Exception {
    disconnect();

    if (processor != null) {
      processor.shutdownNow();
    }
  }

  @Override
  public synchronized boolean isStarted() {
    return started.get() && isRunning();
  }

  @Override
  protected synchronized boolean isRunning() {
    return processor != null && !processor.isShutdown();
  }

  @Override
  protected synchronized boolean isTerminated() {
    return processor == null || processor.isTerminated();
  }

  protected abstract void connect() throws Exception;

  protected abstract void disconnect() throws Exception;

  protected abstract boolean isConnected();
}
