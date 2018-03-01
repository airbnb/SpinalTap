/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.SourceException;
import com.airbnb.spinaltap.common.util.Filter;
import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.common.util.Validator;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Base abstract implementation of Source.
 *
 * @param <E> The event type produced by the source
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractSource<E extends SourceEvent> extends ListenableSource<E> {
  @Getter protected final String name;
  protected final SourceMetrics metrics;
  protected final AtomicBoolean started = new AtomicBoolean(false);

  private final Mapper<E, List<? extends Mutation<?>>> mutationMapper;
  private final Filter<E> eventFilter;

  @Override
  public void open() {
    try {
      if (isStarted()) {
        log.info("Source {} already started", name);
        return;
      }

      Preconditions.checkState(
          isTerminated(), "Previous processor thread has not terminated for source ", name);

      initialize();
      notifyStart();
      started.set(true);

      start();

      log.info("Started source {}", name);
      metrics.start();
    } catch (Throwable ex) {
      String errorMessage = String.format("Failed to start source %s", name);

      log.error(errorMessage, ex);
      metrics.startFailure(ex);

      close();

      throw new SourceException(errorMessage, ex);
    }
  }

  @Override
  public void close() {
    try {
      stop();
      started.set(false);
      log.info("Stopped source {}", name);
      metrics.stop();
    } catch (Throwable ex) {
      log.error("Failed to stop source " + name, ex);
      metrics.stopFailure(ex);
    }
  }

  @Override
  public void checkpoint(Mutation<?> mutation) {
    try {
      log.info("Checkpoint source {}", name);

      commitCheckpoint(mutation);

      metrics.checkpoint();
    } catch (Throwable ex) {
      String errorMessage = String.format("Failed to checkpoint source %s", name);

      log.error(errorMessage, ex);
      metrics.checkpointFailure(ex);

      throw new SourceException(errorMessage, ex);
    }
  }

  @Override
  public void clear() {
    metrics.clear();
  }

  protected abstract void initialize();

  protected abstract void start() throws Exception;

  protected abstract void stop() throws Exception;

  protected abstract void commitCheckpoint(Mutation<?> mutation);

  protected abstract boolean isRunning();

  protected abstract boolean isTerminated();

  /**
   * Processes an event produced by the source and notifies subscribers of the corresponding
   * mutations
   */
  public void processEvent(E event) {
    try {
      if (!eventFilter.apply(event)) {
        log.debug("Event filtered from source {}. Skipping. event={}", name, event);
        return;
      }

      notifyEvent(event);

      metrics.eventReceived(event);
      log.debug("Received event from source {}. event={}", name, event);

      long start = System.currentTimeMillis();

      notifyMutations(mutationMapper.map(event));

      long end = System.currentTimeMillis();
      metrics.processEventTime(event, end - start);

    } catch (Exception ex) {
      if (!isStarted()) {
        // Do not process the exception if streaming has stopped
        return;
      }

      String errorMessage = String.format("Failed to process event from source %s", name);

      log.error(errorMessage, ex);
      metrics.eventFailure(ex);

      notifyError(ex);

      throw new SourceException(errorMessage, ex);
    }
  }

  public void addEventValidator(Validator<E> validator) {
    addListener(
        new Listener() {
          @Override
          public void onStart() {
            validator.reset();
          }

          @Override
          @SuppressWarnings("unchecked")
          public void onEvent(SourceEvent event) {
            validator.validate((E) event);
          }
        });
  }

  public <M extends Mutation<?>> void addMutationValidator(Validator<M> validator) {
    addListener(
        new Listener() {
          @Override
          public void onStart() {
            validator.reset();
          }

          @Override
          @SuppressWarnings("unchecked")
          public void onMutation(List<? extends Mutation<?>> mutations) {
            mutations.forEach(mutation -> validator.validate((M) mutation));
          }
        });
  }
}
