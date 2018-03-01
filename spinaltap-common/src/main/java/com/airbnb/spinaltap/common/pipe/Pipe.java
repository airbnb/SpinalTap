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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Responsible for managing source and routing mutations to destination */
@Slf4j
@RequiredArgsConstructor
public class Pipe {
  private static final int CHECKPOINT_PERIOD_SECONDS = 60;
  private static final int KEEP_ALIVE_PERIOD_SECONDS = 5;
  private static final int EXECUTOR_DELAY_SECONDS = 5;

  @Getter private final Source source;
  private final Destination destination;
  private final PipeMetrics metrics;

  private final Source.Listener sourceListener = new SourceListener();
  private final Destination.Listener destinationListener = new DestinationListener();

  private ScheduledExecutorService checkpointExecutor;
  private ScheduledExecutorService keepAliveExecutor;

  public String getName() {
    return source.getName();
  }

  public Mutation<?> getLastMutation() {
    return destination.getLastPublishedMutation();
  }

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

  private synchronized void open() {
    destination.open();
    source.open();

    metrics.open();
  }

  private synchronized void close() {
    source.close();
    destination.close();

    checkpoint();

    metrics.close();
  }

  public void removeSourceListener() {
    source.removeListener(sourceListener);
  }

  public boolean isStarted() {
    return source.isStarted() && destination.isStarted();
  }

  public void checkpoint() {
    source.checkpoint(getLastMutation());
  }

  class SourceListener extends Source.Listener {
    public void onMutation(List<? extends Mutation<?>> mutations) {
      destination.send(mutations);
    }

    public void onError(Throwable error) {
      close();
    }
  }

  class DestinationListener extends Destination.Listener {
    public void onError(Exception ex) {
      destination.close();
    }
  }
}
