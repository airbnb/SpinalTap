/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a collection of {@link SourceState} checkpoints. This is used to track changes to
 * state, and enables rolling back to previous checkpoints if needed (ex: in case of erroneous
 * behavior or data loss).
 *
 * <p>The state history is tracked in-memory in a {@link java.util.Deque}. Add and remove operations
 * are performed on the tail (stack ordering). When capacity is reached, entries are evicted from
 * the head (queue ordering) .
 *
 * <p>The state history is persisted in the {@link Repository} implement provided on construction.
 * Changes are committed on every add or remove operation to ensure durability. The in-memory
 * collection is mainly employed as a caching solution to optimize read operations and reduce
 * request load on the backing storage.
 */
@Slf4j
@AllArgsConstructor
public final class StateHistory {
  private static final int DEFAULT_CAPACITY = 1440;

  @NonNull private final String sourceName;

  @Min(1)
  private final int capacity;

  @NonNull private final Repository<Collection<SourceState>> repository;
  @NonNull private final MysqlSourceMetrics metrics;
  @NonNull private final Deque<SourceState> stateHistory;

  public StateHistory(
      @NonNull final String sourceName,
      @NonNull final Repository<Collection<SourceState>> repository,
      @NonNull final MysqlSourceMetrics metrics) {
    this(sourceName, DEFAULT_CAPACITY, repository, metrics);
  }

  public StateHistory(
      @NonNull final String sourceName,
      @Min(1) final int capacity,
      @NonNull final Repository<Collection<SourceState>> repository,
      @NonNull final MysqlSourceMetrics metrics) {
    Preconditions.checkState(capacity > 0);

    this.sourceName = sourceName;
    this.capacity = capacity;
    this.repository = repository;
    this.metrics = metrics;
    this.stateHistory = Queues.newArrayDeque(getPreviousStates());
  }

  /** Adds a new {@link SourceState} entry to the history. */
  public void add(final SourceState state) {
    if (stateHistory.size() >= capacity) {
      stateHistory.removeFirst();
    }

    stateHistory.addLast(state);
    save();
  }

  /** Removes the most recently added {@link SourceState} entry from the history. */
  public SourceState removeLast() {
    return removeLast(1);
  }

  /**
   * Removes the last N most recently added {@link StateHistory} entries from the history.
   *
   * @param count the number of records to remove.
   * @return the last removed {@link SourceState}.
   */
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

  /** Clears the state history */
  public void clear() {
    if (stateHistory.isEmpty()) {
      return;
    }

    stateHistory.clear();
    save();
  }

  /** @return {@code True} if the history is empty, else {@code False}. */
  public boolean isEmpty() {
    return stateHistory.isEmpty();
  }

  /** @return the current size of the state history. */
  public int size() {
    return stateHistory.size();
  }

  /** @return a collection representing the {@link SourceState}s currently in the state history. */
  private Collection<SourceState> getPreviousStates() {
    try {
      return repository.exists() ? repository.get() : Collections.emptyList();
    } catch (Exception ex) {
      log.error("Failed to read state history for source " + sourceName, ex);
      metrics.stateReadFailure(ex);

      throw new RuntimeException(ex);
    }
  }

  /** Persists the state history in the backing repository. */
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

      throw new RuntimeException(ex);
    }
  }
}
