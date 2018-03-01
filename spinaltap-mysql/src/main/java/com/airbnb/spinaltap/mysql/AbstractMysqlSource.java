/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.source.AbstractDataStoreSource;
import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.filter.MysqlEventFilter;
import com.airbnb.spinaltap.mysql.event.mapper.MysqlMutationMapper;
import com.airbnb.spinaltap.mysql.exception.InvalidBinlogPositionException;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Reads mysql events from database host and transforms them to mutations. */
@Slf4j
public abstract class AbstractMysqlSource extends AbstractDataStoreSource<BinlogEvent> {
  public static final BinlogFilePos LATEST_BINLOG_POS = new BinlogFilePos(null, 0, 0);
  public static final BinlogFilePos EARLIEST_BINLOG_POS = new BinlogFilePos("", 4, 4);

  private static final int STATE_ROLLBACK_BACKOFF_RATE = 2;

  @Getter private final DataSource dataSource;
  private final TableCache tableCache;
  private final StateRepository stateRepository;
  private final BinlogFilePos initialBinlogFilePosition;
  protected final MysqlSourceMetrics metrics;

  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private AtomicReference<SourceState> lastSavedState;

  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private AtomicReference<Transaction> lastTransaction;

  private final AtomicLong currentLeaderEpoch;

  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private final StateHistory stateHistory;

  private AtomicInteger stateRollbackCount = new AtomicInteger(1);

  public AbstractMysqlSource(
      String name,
      DataSource dataSource,
      Set<String> tableNames,
      TableCache tableCache,
      StateRepository stateRepository,
      StateHistory stateHistory,
      BinlogFilePos initialBinlogFilePosition,
      SchemaTracker schemaTracker,
      MysqlSourceMetrics metrics,
      AtomicLong currentLeaderEpoch,
      AtomicReference<Transaction> lastTransaction,
      AtomicReference<SourceState> lastSavedState) {
    super(
        name,
        metrics,
        MysqlMutationMapper.create(
            dataSource,
            tableCache,
            schemaTracker,
            currentLeaderEpoch,
            new AtomicReference<>(),
            lastTransaction,
            metrics),
        MysqlEventFilter.create(tableCache, tableNames, lastSavedState));

    this.dataSource = dataSource;
    this.tableCache = tableCache;
    this.stateRepository = stateRepository;
    this.stateHistory = stateHistory;
    this.metrics = metrics;
    this.currentLeaderEpoch = currentLeaderEpoch;
    this.lastTransaction = lastTransaction;
    this.lastSavedState = lastSavedState;
    this.initialBinlogFilePosition = initialBinlogFilePosition;
  }

  public abstract void setPosition(BinlogFilePos pos);

  protected void initialize() {
    tableCache.clear();

    SourceState state = getSavedState();

    lastSavedState.set(state);
    lastTransaction.set(
        new Transaction(state.getLastTimestamp(), state.getLastOffset(), state.getLastPosition()));

    setPosition(state.getLastPosition());
  }

  /** Resets the source to the last valid state */
  void resetToLastValidState() {
    if (stateHistory.size() >= stateRollbackCount.get()) {
      SourceState newState = stateHistory.removeLast(stateRollbackCount.get());
      saveState(newState);

      metrics.resetSourcePosition();
      log.info("Reset source {} position to {}.", name, newState.getLastPosition());

      stateRollbackCount.accumulateAndGet(
          STATE_ROLLBACK_BACKOFF_RATE, (value, rate) -> value * rate);

    } else {
      stateHistory.clear();
      saveState(getEarliestState());

      metrics.resetEarliestPosition();
      log.info("Reset source {} position to earliest.", name);
    }
  }

  private SourceState getEarliestState() {
    return new SourceState(0L, 0L, currentLeaderEpoch.get(), EARLIEST_BINLOG_POS);
  }

  protected void onDeserializationError(Exception ex) {
    metrics.deserializationFailure(ex);

    // Fail on deserialization errors and restart source from last checkpoint
    Throwables.propagate(ex);
  }

  protected void onCommunicationError(Exception ex) {
    metrics.communicationFailure(ex);

    if (ex instanceof InvalidBinlogPositionException) {
      resetToLastValidState();
    }
  }

  public void commitCheckpoint(Mutation<?> mutation) {
    SourceState savedState = lastSavedState.get();
    if (mutation == null || savedState == null) {
      return;
    }

    Preconditions.checkState(mutation instanceof MysqlMutation);
    MysqlMutationMetadata metadata = ((MysqlMutation) mutation).getMetadata();

    // Make sure we are saving at a higher watermark
    if (savedState.getLastOffset() >= metadata.getId()) {
      return;
    }

    SourceState newState =
        new SourceState(
            metadata.getTimestamp(),
            metadata.getId(),
            currentLeaderEpoch.get(),
            metadata.getLastTransaction().getPosition());

    saveState(newState);

    stateHistory.add(newState);
    stateRollbackCount.set(1);
  }

  void saveState(SourceState state) {
    stateRepository.save(state);
    lastSavedState.set(state);
  }

  SourceState getSavedState() {
    SourceState state = stateRepository.read();

    return state != null
        ? state
        : new SourceState(0L, 0L, currentLeaderEpoch.get(), initialBinlogFilePosition);
  }
}
