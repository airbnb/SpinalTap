/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.DestinationException;
import com.airbnb.spinaltap.common.util.ConcurrencyUtil;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link Destination} implement with a bounded in-memory buffer. This is used to solve
 * the Producer-Consumer problem between {@link com.airbnb.spinaltap.common.source.Source} and
 * {@link Destination} processing, resulting in higher concurrency and reducing overall event
 * latency.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BufferedDestination extends ListenableDestination {
  @NonNull private final Destination destination;
  @NonNull private final DestinationMetrics metrics;
  @NonNull private final BlockingQueue<List<? extends Mutation<?>>> mutationBuffer;

  private ExecutorService consumer;

  public BufferedDestination(
      @Min(1) final int bufferSize,
      @NonNull final Destination destination,
      @NonNull final DestinationMetrics metrics) {
    this(destination, metrics, new ArrayBlockingQueue<>(bufferSize, true));

    destination.addListener(
        new Listener() {
          public void onError(Exception ex) {
            notifyError(ex);
          }
        });
  }

  public int getRemainingCapacity() {
    return mutationBuffer.remainingCapacity();
  }

  /**
   * Adds a list of {@link Mutation}s to the buffer, to be sent to the underlying {@link
   * Destination}. This action is blocking, i.e. thread will wait if the buffer is full.
   *
   * @param mutations the mutations to send
   */
  @Override
  public void send(@NonNull final List<? extends Mutation<?>> mutations) {
    try {
      if (mutations.isEmpty()) {
        return;
      }

      Preconditions.checkState(destination.isStarted(), "Destination is not started!");

      final Stopwatch stopwatch = Stopwatch.createStarted();
      final Mutation.Metadata metadata = mutations.get(0).getMetadata();

      if (mutationBuffer.remainingCapacity() == 0) {
        metrics.bufferFull(metadata);
      }

      mutationBuffer.put(mutations);

      metrics.bufferSize(mutationBuffer.size(), metadata);

      stopwatch.stop();
      final long time = stopwatch.elapsed(TimeUnit.MILLISECONDS);

      metrics.sendTime(time);

    } catch (Exception ex) {
      log.error("Failed to send mutations.", ex);
      metrics.sendFailed(ex);

      throw new DestinationException("Failed to send mutations", ex);
    }
  }

  /** @return the last published {@link Mutation} to the underlying {@link Destination}. */
  public Mutation<?> getLastPublishedMutation() {
    return destination.getLastPublishedMutation();
  }

  /**
   * Process all {@link Mutation}s in the buffer, and sends them to the underlying {@link
   * Destination}.
   */
  void processMutations() throws Exception {
    final List<List<? extends Mutation<?>>> mutationBatches = new ArrayList<>();

    // Execute "take" first to block if there are no mutations present (avoid a busy wait)
    mutationBatches.add(mutationBuffer.take());
    mutationBuffer.drainTo(mutationBatches);

    destination.send(mutationBatches.stream().flatMap(List::stream).collect(Collectors.toList()));
  }

  private void execute() {
    try {
      while (isRunning()) {
        processMutations();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.info("Thread interrupted");
    } catch (Exception ex) {
      metrics.sendFailed(ex);
      log.info("Failed to send mutation", ex);

      notifyError(ex);
    }

    log.info("Destination stopped processing mutations");
  }

  public boolean isRunning() {
    return consumer != null && !consumer.isShutdown();
  }

  public boolean isTerminated() {
    return consumer == null || consumer.isTerminated();
  }

  @Override
  public boolean isStarted() {
    return destination.isStarted() && isRunning();
  }

  @Override
  public void open() {
    if (isStarted()) {
      log.info("Destination is already started.");
      return;
    }

    try {
      Preconditions.checkState(isTerminated(), "Previous consumer thread has not terminated.");

      mutationBuffer.clear();
      destination.open();

      consumer =
          Executors.newSingleThreadExecutor(
              new ThreadFactoryBuilder().setNameFormat("buffered-destination-consumer").build());

      consumer.execute(this::execute);

      log.info("Started destination.");
    } catch (Exception ex) {
      log.error("Failed to start destination.", ex);
      metrics.startFailure(ex);

      close();

      throw new DestinationException("Failed to start destination", ex);
    }
  }

  @Override
  public void close() {
    if (!isTerminated()) {
      ConcurrencyUtil.shutdownGracefully(consumer, 2, TimeUnit.SECONDS);
    }

    destination.close();
    mutationBuffer.clear();
  }

  public void clear() {
    destination.clear();
    metrics.clear();
  }
}
