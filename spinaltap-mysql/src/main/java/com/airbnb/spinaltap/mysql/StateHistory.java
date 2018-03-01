/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Queues;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class StateHistory {
  private static final int DEFAULT_CAPACITY = 1440;

  private final String sourceName;
  private final int capacity;
  private final Repository<Collection<SourceState>> repository;
  private final MysqlSourceMetrics metrics;
  private final Deque<SourceState> stateHistory;

  public StateHistory(
      String sourceName,
      Repository<Collection<SourceState>> repository,
      MysqlSourceMetrics metrics) {
    this(sourceName, DEFAULT_CAPACITY, repository, metrics);
  }

  public StateHistory(
      String sourceName,
      int capacity,
      Repository<Collection<SourceState>> repository,
      MysqlSourceMetrics metrics) {
    Preconditions.checkState(capacity > 0);

    this.sourceName = sourceName;
    this.capacity = capacity;
    this.repository = repository;
    this.metrics = metrics;

    this.stateHistory = Queues.newArrayDeque(getPreviousStates());
  }

  public void add(SourceState state) {
    if (stateHistory.size() >= capacity) {
      stateHistory.removeFirst();
    }

    stateHistory.addLast(state);
    save();
  }

  public SourceState removeLast() {
    return removeLast(1);
  }

  public SourceState removeLast(int count) {
    Preconditions.checkArgument(count > 0, "Count should be greater than 0");
    Preconditions.checkState(!stateHistory.isEmpty(), "The state history is empty");
    Preconditions.checkState(stateHistory.size() >= count, "Count is larger than history size");

    SourceState state = stateHistory.removeLast();
    for (int i = 1; i < count; i++) {
      state = stateHistory.removeLast();
    }

    save();
    return state;
  }

  public void clear() {
    if (stateHistory.isEmpty()) {
      return;
    }

    stateHistory.clear();
    save();
  }

  public boolean isEmpty() {
    return stateHistory.isEmpty();
  }

  public int size() {
    return stateHistory.size();
  }

  private Collection<SourceState> getPreviousStates() {
    Collection<SourceState> previousStates = null;

    try {
      if (repository.exists()) {
        previousStates = repository.get();
      }
    } catch (Exception ex) {
      log.error("Failed to read state history for source " + sourceName, ex);
      metrics.stateReadFailure(ex);

      Throwables.propagate(ex);
    }

    return previousStates != null ? previousStates : Collections.emptyList();
  }

  private void save() {
    try {
      if (repository.exists()) {
        repository.set(stateHistory);
      } else {
        repository.create(stateHistory);
      }
    } catch (Exception ex) {
      log.error("Failed to save state history for source " + sourceName, ex);
      metrics.stateSaveFailure(ex);

      Throwables.propagate(ex);
    }
  }
}
