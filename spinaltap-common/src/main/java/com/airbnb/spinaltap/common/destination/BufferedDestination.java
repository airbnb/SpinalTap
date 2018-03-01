/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.DestinationException;
import com.airbnb.spinaltap.common.util.SpinalTapConcurrencyUtil;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BufferedDestination extends ListenableDestination {
  private final Destination destination;
  private final DestinationMetrics metrics;

  private final BlockingQueue<List<? extends Mutation<?>>> mutationBuffer;

  private ExecutorService consumer;

  private Listener destinationListener =
      new Listener() {
        public void onError(Exception ex) {
          notifyError(ex);
        }
      };

  public BufferedDestination(int bufferSize, Destination destination, DestinationMetrics metrics) {
    this.destination = destination;
    this.metrics = metrics;

    this.mutationBuffer = new ArrayBlockingQueue<>(bufferSize, true);
    destination.addListener(destinationListener);
  }

  public int getRemainingCapacity() {
    return mutationBuffer.remainingCapacity();
  }

  public void send(List<? extends Mutation<?>> mutations) {
    try {
      if (mutations.isEmpty()) {
        return;
      }

      Preconditions.checkState(destination.isStarted(), "Destination is not started!");

      long start = System.currentTimeMillis();
      Mutation.Metadata metadata = mutations.get(0).getMetadata();

      if (mutationBuffer.remainingCapacity() == 0) {
        metrics.bufferFull(metadata);
      }

      mutationBuffer.put(mutations);

      metrics.bufferSize(mutationBuffer.size(), metadata);

      long end = System.currentTimeMillis();
      metrics.sendTime(end - start);
    } catch (Exception ex) {
      log.error("Failed to send mutations", ex);
      metrics.sendFailed(ex);

      throw new DestinationException("Failed to send mutations", ex);
    }
  }

  public Mutation<?> getLastPublishedMutation() {
    return destination.getLastPublishedMutation();
  }

  void processMutations() throws Exception {
    List<List<? extends Mutation<?>>> mutationBatches = new ArrayList<>();

    // Execute take first to block if there are no mutations present (avoid a busy wait)
    mutationBatches.add(mutationBuffer.take());
    mutationBuffer.drainTo(mutationBatches);

    List<Mutation<?>> mutations = new ArrayList<>();
    mutationBatches.forEach(mutations::addAll);
    destination.send(mutations);
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
      log.info("Destination is already started");
      return;
    }

    try {
      Preconditions.checkState(isTerminated(), "Previous consumer thread has not terminated");

      mutationBuffer.clear();
      destination.open();

      consumer =
          Executors.newSingleThreadExecutor(
              new ThreadFactoryBuilder().setNameFormat("buffered-destination-consumer").build());

      consumer.execute(this::execute);

      log.info("Started destination");
    } catch (Exception ex) {
      log.error("Failed to start destination", ex);
      metrics.startFailure(ex);

      close();

      throw new DestinationException("Failed to start destination", ex);
    }
  }

  @Override
  public void close() {
    if (!isTerminated()) {
      SpinalTapConcurrencyUtil.shutdownGracefully(consumer, 2, TimeUnit.SECONDS);
    }
    destination.close();
    // Clear buffer
    mutationBuffer.clear();
  }

  public void clear() {
    destination.clear();
    metrics.clear();
  }
}
