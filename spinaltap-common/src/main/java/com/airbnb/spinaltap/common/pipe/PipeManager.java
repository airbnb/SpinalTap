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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class PipeManager {
  private final Table<String, String, List<Pipe>> pipeTable =
      Tables.newCustomTable(Maps.newConcurrentMap(), Maps::newConcurrentMap);

  private final Executor executor = Executors.newSingleThreadExecutor();

  public void addPipe(String name, Pipe pipe) {
    addPipe(name, getDefaultPartition(name), pipe);
  }

  public void addPipe(String name, String partition, Pipe pipe) {
    addPipes(name, partition, Collections.singletonList(pipe));
  }

  public void addPipes(String name, String partition, List<Pipe> pipes) {
    log.debug("Adding pipes for {} / {}", name, partition);

    pipes.forEach(Pipe::start);
    pipeTable.put(name, partition, pipes);

    log.info("Added pipes for {} / {}", name, partition);
  }

  private static String getDefaultPartition(String name) {
    return String.format("%s_%d", name, 0);
  }

  public boolean contains(String name) {
    return pipeTable.containsRow(name);
  }

  public boolean contains(String name, String partition) {
    return pipeTable.contains(name, partition);
  }

  public boolean isEmpty() {
    return pipeTable.isEmpty();
  }

  public Set<String> getPartitions(String name) {
    return pipeTable.row(name).keySet();
  }

  public void removePipe(String name, String partition) {
    log.debug("Removing pipes for {} / {}", name, partition);

    List<Pipe> pipes = pipeTable.get(name, partition);
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

  public void executeAsync(Runnable operation) {
    executor.execute(operation);
  }

  public void start() throws Exception {
    log.debug("Starting pipe manager");

    startPipes();

    log.info("Started pipe manager");
  }

  public void stop() {
    log.debug("Stopping pipe manager");

    stopPipes();

    log.info("Stopped pipe manager");
  }

  public void startPipes() {
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
  }

  public void stopPipes() {
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
  }
}
