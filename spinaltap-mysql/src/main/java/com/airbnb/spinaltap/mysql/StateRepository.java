/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Represents a repository for a {@link SourceState} record. */
@Slf4j
@RequiredArgsConstructor
public class StateRepository {
  @NonNull private final String sourceName;
  @NonNull private final Repository<SourceState> repository;
  @NonNull private final MysqlSourceMetrics metrics;

  /** Saves or updates the {@link SourceState} record in the repository */
  public void save(@NonNull final SourceState state) {
    try {
      repository.update(
          state,
          (currentValue, nextValue) -> {
            if (currentValue.getCurrentLeaderEpoch() > nextValue.getCurrentLeaderEpoch()) {
              log.warn("Will not update mysql state: current={}, next={}", currentValue, nextValue);
              return currentValue;
            }
            return nextValue;
          });

    } catch (Exception ex) {
      log.error("Failed to save state for source " + sourceName, ex);
      metrics.stateSaveFailure(ex);
      throw new RuntimeException(ex);
    }

    log.info("Saved state for source {}. state={}", sourceName, state);
    metrics.stateSave();
  }

  /** @return the {@link SourceState} record present in the repository. */
  public SourceState read() {
    SourceState state = null;

    try {
      if (repository.exists()) {
        state = repository.get();
      } else {
        log.info("State does not exist for source {}", sourceName);
      }
    } catch (Exception ex) {
      log.error("Failed to read state for source " + sourceName, ex);
      metrics.stateReadFailure(ex);
      throw new RuntimeException(ex);
    }

    log.debug("Read state for source {}. state={}", sourceName, state);
    metrics.stateRead();
    return state;
  }
}
