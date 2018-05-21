/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.pipe;

import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for managing a collection of {@link Pipe}s for a set of resources.
 *
 * <p>A resource is typically associated with a data source, ex: a MySQL database
 */
@Slf4j
@NoArgsConstructor
public class PipeManager {
  private static final long CHECK_STOPPED_WAIT_MILLISEC = 1000L;
  private static final int CHECK_STOPPED_WAIT_TIMEOUT_SECONDS = 30;
  /**
   * Mapped table of [Resource][Partition][Pipes]. In other words, registered resource will have a
   * set of partitions, each of which will have a collection of {@link Pipe}s registered.
   */
  private final Table<String, String, List<Pipe>> pipeTable =
      Tables.newCustomTable(Maps.newConcurrentMap(), Maps::newConcurrentMap);

  private final Executor executor = Executors.newSingleThreadExecutor();

  /**
   * Registers a pipe for the given resource.
   *
   * @param name the resource name
   * @param pipe the pipe
   */
  public void addPipe(@NonNull final String name, @NonNull final Pipe pipe) {
    addPipe(name, getDefaultPartition(name), pipe);
  }

  /**
   * Registers a {@link Pipe} for the given resource partition
   *
   * @param name The resource name
   * @param partition the partition name
   * @param pipe the {@link Pipe}
   */
  public void addPipe(
      @NonNull final String name, @NonNull final String partition, @NonNull final Pipe pipe) {
    addPipes(name, partition, Collections.singletonList(pipe));
  }

  /**
   * Registers a list of {@link Pipe}s for the give resource partition
   *
   * @param name The resource name
   * @param partition the partition name
   * @param pipes the list of {@link Pipe}s
   */
  public void addPipes(
      @NonNull final String name,
      @NonNull final String partition,
      @NonNull final List<Pipe> pipes) {
    log.debug("Adding pipes for {} / {}", name, partition);

    pipes.forEach(Pipe::start);
    pipeTable.put(name, partition, pipes);

    log.info("Added pipes for {} / {}", name, partition);
  }

  private static String getDefaultPartition(final String name) {
    return String.format("%s_%d", name, 0);
  }

  /** @return whether the given resource is registered. */
  public boolean contains(@NonNull final String name) {
    return pipeTable.containsRow(name);
  }

  /** @return whether the given resource partition is registered. */
  public boolean contains(@NonNull final String name, @NonNull final String partition) {
    return pipeTable.contains(name, partition);
  }

  public boolean isEmpty() {
    return pipeTable.isEmpty();
  }

  /** @return all partitions for a given registered resource. */
  public Set<String> getPartitions(@NonNull final String name) {
    return pipeTable.row(name).keySet();
  }

  /**
   * Removes a resource partition.
   *
   * @param name the resource
   * @param partition the partition
   */
  public void removePipe(@NonNull final String name, @NonNull final String partition) {
    log.debug("Removing pipes for {} / {}", name, partition);

    final List<Pipe> pipes = pipeTable.get(name, partition);
    if (pipes == null || pipes.isEmpty()) {
      log.info("Pipes do not exist for {} / {}", name, partition);
      return;
    }

    pipeTable.remove(name, partition);
    pipes.forEach(
        pipe -> {
          // Remove source listener here to avoid deadlock, as this may be run in a different thread
          // from source-processor thread
          pipe.removeSourceListener();
          pipe.stop();
        });

    log.info("Removed pipes for {} / {}", name, partition);
  }

  public void executeAsync(@NonNull final Runnable operation) {
    executor.execute(operation);
  }

  /** Starts all {@link Pipe}s for all managed resources. */
  public void start() throws Exception {
    log.debug("Starting pipe manager");

    pipeTable
        .values()
        .parallelStream()
        .flatMap(Collection::parallelStream)
        .forEach(
            pipe -> {
              try {
                pipe.start();
              } catch (Exception ex) {
                log.error("Failed to start pipe " + pipe.getName(), ex);
              }
            });

    log.info("Started pipe manager");
  }

  /** Starts all {@link Pipe}s for all managed resources. */
  public void stop() {
    log.debug("Stopping pipe manager");

    pipeTable
        .values()
        .parallelStream()
        .flatMap(Collection::parallelStream)
        .forEach(
            pipe -> {
              try {
                pipe.stop();
              } catch (Exception ex) {
                log.error("Failed to stop pipe " + pipe.getName(), ex);
              }
            });

    log.info("Stopped pipe manager");
  }

  public boolean allPipesStopped() {
    return pipeTable
        .values()
        .parallelStream()
        .flatMap(Collection::parallelStream)
        .noneMatch(Pipe::isStarted);
  }

  public void waitUntilStopped() throws Exception {
    int periods = 0;
    while (!allPipesStopped()) {
      if (CHECK_STOPPED_WAIT_MILLISEC * periods++ >= 1000 * CHECK_STOPPED_WAIT_TIMEOUT_SECONDS) {
        throw new TimeoutException(
            String.format(
                "Not all pipes were stopped completely within %s seconds",
                CHECK_STOPPED_WAIT_TIMEOUT_SECONDS));
      }
      Thread.sleep(CHECK_STOPPED_WAIT_MILLISEC);
    }
  }
}
