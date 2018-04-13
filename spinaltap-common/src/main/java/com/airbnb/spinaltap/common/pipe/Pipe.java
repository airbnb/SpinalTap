/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.pipe;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.destination.Destination;
import com.airbnb.spinaltap.common.source.Source;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for managing event streaming from a {@link com.airbnb.spinaltap.common.source.Source}
 * to a given {@link com.airbnb.spinaltap.common.destination.Destination}, as well as the lifecycle
 * of both components.
 */
@Slf4j
@RequiredArgsConstructor
public class Pipe {
  private static final int CHECKPOINT_PERIOD_SECONDS = 60;
  private static final int KEEP_ALIVE_PERIOD_SECONDS = 5;
  private static final int EXECUTOR_DELAY_SECONDS = 5;

  @NonNull @Getter private final Source source;
  @NonNull private final Destination destination;
  @NonNull private final PipeMetrics metrics;

  private final Source.Listener sourceListener = new SourceListener();
  private final Destination.Listener destinationListener = new DestinationListener();

  /** The checkpoint executor that periodically checkpoints the state of the source. */
  private ScheduledExecutorService checkpointExecutor;

  /**
   * The keep-alive executor that periodically checks the pipe is alive, and otherwise restarts it.
   */
  private ScheduledExecutorService keepAliveExecutor;

  /** @return The name of the pipe. */
  public String getName() {
    return source.getName();
  }

  /** @return the last mutation successfully sent to the pipe's {@link Destination}. */
  public Mutation<?> getLastMutation() {
    return destination.getLastPublishedMutation();
  }

  /** Starts event streaming for the pipe. */
  public void start() {
    source.addListener(sourceListener);
    destination.addListener(destinationListener);

    open();

    scheduleKeepAliveExecutor();
    scheduleCheckpointExecutor();

    metrics.start();
  }

  private void scheduleKeepAliveExecutor() {
    if (keepAliveExecutor != null && !keepAliveExecutor.isShutdown()) {
      log.debug("Keep-alive executor is running");
      return;
    }

    keepAliveExecutor =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(getName() + "-pipe-keep-alive-executor")
                .build());

    keepAliveExecutor.scheduleAtFixedRate(
        () -> {
          try {
            if (isStarted()) {
              log.info("Pipe {} is alive", getName());
            } else {
              open();
            }
          } catch (Exception ex) {
            log.error("Failed to open pipe " + getName(), ex);
          }
        },
        EXECUTOR_DELAY_SECONDS,
        KEEP_ALIVE_PERIOD_SECONDS,
        TimeUnit.SECONDS);
  }

  private void scheduleCheckpointExecutor() {
    if (checkpointExecutor != null && !checkpointExecutor.isShutdown()) {
      log.debug("Checkpoint executor is running");
      return;
    }

    checkpointExecutor =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(getName() + "-pipe-checkpoint-executor")
                .build());

    checkpointExecutor.scheduleAtFixedRate(
        () -> {
          try {
            checkpoint();
          } catch (Exception ex) {
            log.error("Failed to checkpoint pipe " + getName(), ex);
          }
        },
        EXECUTOR_DELAY_SECONDS,
        CHECKPOINT_PERIOD_SECONDS,
        TimeUnit.SECONDS);
  }

  /** Stops event streaming for the pipe. */
  public void stop() {
    if (keepAliveExecutor != null) {
      keepAliveExecutor.shutdownNow();
    }

    if (checkpointExecutor != null) {
      checkpointExecutor.shutdownNow();
    }

    source.clear();
    destination.clear();

    close();

    source.removeListener(sourceListener);
    destination.removeListener(destinationListener);

    metrics.stop();
  }

  /** Opens the {@link Source} and {@link Destination} to initiate event streaming */
  private synchronized void open() {
    destination.open();
    source.open();

    metrics.open();
  }

  /**
   * Closes the {@link Source} and {@link Destination} to terminate event streaming, and checkpoints
   * the last recorded {@link Source} state.
   */
  private synchronized void close() {
    source.close();
    destination.close();

    checkpoint();

    metrics.close();
  }

  public void removeSourceListener() {
    source.removeListener(sourceListener);
  }

  /** @return whether the pipe is currently streaming events */
  public boolean isStarted() {
    return source.isStarted() && destination.isStarted();
  }

  /** Checkpoints the source according to the last streamed {@link Mutation} in the pipe */
  public void checkpoint() {
    source.checkpoint(getLastMutation());
  }

  final class SourceListener extends Source.Listener {
    public void onMutation(List<? extends Mutation<?>> mutations) {
      destination.send(mutations);
    }

    public void onError(Throwable error) {
      close();
    }
  }

  final class DestinationListener extends Destination.Listener {
    public void onError(Exception ex) {
      destination.close();
    }
  }
}
