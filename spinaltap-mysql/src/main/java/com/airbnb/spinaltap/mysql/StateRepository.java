/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StateRepository {
  private final String sourceName;
  private final Repository<SourceState> repository;
  private final MysqlSourceMetrics metrics;

  /** Saves the source state to the repository. Creates a new entry if one does not exist */
  public void save(SourceState state) {
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

  /** Reads the source state from the repository. Returns a new state if one does not exist */
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
