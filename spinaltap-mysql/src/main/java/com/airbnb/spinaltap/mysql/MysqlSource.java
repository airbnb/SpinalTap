/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.source.AbstractDataStoreSource;
import com.airbnb.spinaltap.common.source.MysqlSourceState;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.filter.MysqlEventFilter;
import com.airbnb.spinaltap.mysql.event.mapper.MysqlMutationMapper;
import com.airbnb.spinaltap.mysql.exception.InvalidBinlogPositionException;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Base implement of a MySQL {@link com.airbnb.spinaltap.common.source.Source} that streams events
 * from a given binlog for a specified database host, and transforms them to {@link Mutation}s.
 */
@Slf4j
public abstract class MysqlSource extends AbstractDataStoreSource<BinlogEvent> {
  /** Represents the latest binlog position in the mysql-binlog-connector client. */
  public static final BinlogFilePos LATEST_BINLOG_POS = new BinlogFilePos(null, 0, 0);

  /** Represents the earliest binlog position in the mysql-binlog-connector client. */
  public static final BinlogFilePos EARLIEST_BINLOG_POS = new BinlogFilePos("", 4, 4);

  /** The backoff rate when conducting rollback in the {@link StateHistory}. */
  private static final int STATE_ROLLBACK_BACKOFF_RATE = 2;

  /** The {@link DataSource} representing the database host the source is streaming events from. */
  @NonNull @Getter private final DataSource dataSource;

  /**
   * The {@link TableCache} tracking {@link com.airbnb.spinaltap.mysql.mutation.schema.Table}
   * metadata for the streamed source events.
   */
  @NonNull private final TableCache tableCache;

  /** The {@link StateRepository} where the {@link MysqlSourceState} is committed to. */
  @NonNull private final StateRepository<MysqlSourceState> stateRepository;

  /** The initial {@link BinlogFilePos} to start streaming from for the source. */
  @NonNull private final BinlogFilePos initialBinlogFilePosition;

  @NonNull protected final MysqlSourceMetrics metrics;

  /** The last checkpointed {@link MysqlSourceState} for the source. */
  @NonNull
  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private final AtomicReference<MysqlSourceState> lastSavedState;

  /** The last MySQL {@link Transaction} seen so far from the streamed events. */
  @NonNull
  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private final AtomicReference<Transaction> lastTransaction;

  /** The leader epoch of the current node processing the source stream. */
  @NonNull private final AtomicLong currentLeaderEpoch;

  /** The {@link StateHistory} of checkpointed {@link MysqlSourceState}s. */
  @NonNull
  @VisibleForTesting
  @Getter(AccessLevel.PACKAGE)
  private final StateHistory<MysqlSourceState> stateHistory;

  private final MysqlSchemaManager schemaManager;

  /**
   * The number of {@link MysqlSourceState} entries to remove from {@link StateHistory} on rollback.
   */
  private final AtomicInteger stateRollbackCount = new AtomicInteger(1);

  public MysqlSource(
      @NonNull final String name,
      @NonNull final DataSource dataSource,
      @NonNull final Set<String> tableNames,
      @NonNull final TableCache tableCache,
      @NonNull final StateRepository<MysqlSourceState> stateRepository,
      @NonNull final StateHistory<MysqlSourceState> stateHistory,
      @NonNull final BinlogFilePos initialBinlogFilePosition,
      @NonNull final MysqlSchemaManager schemaManager,
      @NonNull final MysqlSourceMetrics metrics,
      @NonNull final AtomicLong currentLeaderEpoch,
      @NonNull final AtomicReference<Transaction> lastTransaction,
      @NonNull final AtomicReference<MysqlSourceState> lastSavedState) {
    super(
        name,
        metrics,
        MysqlMutationMapper.create(
            dataSource,
            tableCache,
            schemaManager,
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
    this.schemaManager = schemaManager;
  }

  public abstract void setPosition(BinlogFilePos pos);

  /** Initializes the source and prepares to start streaming. */
  protected void initialize() {
    tableCache.clear();

    MysqlSourceState state = getSavedState();
    log.info("Initializing source {} with saved state {}.", name, state);

    lastSavedState.set(state);
    lastTransaction.set(
        new Transaction(state.getLastTimestamp(), state.getLastOffset(), state.getLastPosition()));

    setPosition(state.getLastPosition());
    schemaManager.initialize(state.getLastPosition());
  }

  /** Resets to the last valid {@link MysqlSourceState} recorded in the {@link StateHistory}. */
  void resetToLastValidState() {
    if (stateHistory.size() >= stateRollbackCount.get()) {
      final MysqlSourceState newState = stateHistory.removeLast(stateRollbackCount.get());
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

  private MysqlSourceState getEarliestState() {
    return new MysqlSourceState(0L, 0L, currentLeaderEpoch.get(), EARLIEST_BINLOG_POS);
  }

  protected void onDeserializationError(final Exception ex) {
    metrics.deserializationFailure(ex);

    // Fail on deserialization errors and restart source from last checkpoint
    throw new RuntimeException(ex);
  }

  protected void onCommunicationError(final Exception ex) {
    metrics.communicationFailure(ex);

    if (ex instanceof InvalidBinlogPositionException) {
      resetToLastValidState();
    }
    throw new RuntimeException(ex);
  }

  /**
   * Checkpoints the {@link MysqlSourceState} for the source at the given {@link Mutation} position.
   */
  public void commitCheckpoint(final Mutation<?> mutation) {
    final MysqlSourceState savedState = lastSavedState.get();
    if (mutation == null || savedState == null) {
      return;
    }

    Preconditions.checkState(mutation instanceof MysqlMutation);
    final MysqlMutationMetadata metadata = ((MysqlMutation) mutation).getMetadata();

    // Make sure we are saving at a higher watermark
    BinlogFilePos mutationPosition = metadata.getFilePos();
    BinlogFilePos savedStatePosition = savedState.getLastPosition();
    if ((BinlogFilePos.shouldCompareUsingFilePosition(mutationPosition, savedStatePosition)
            && savedState.getLastOffset() >= metadata.getId())
        || (mutationPosition.getGtidSet() != null
            && mutationPosition.getGtidSet().isContainedWithin(savedStatePosition.getGtidSet()))) {
      return;
    }

    final MysqlSourceState newState =
        new MysqlSourceState(
            metadata.getTimestamp(),
            metadata.getId(),
            currentLeaderEpoch.get(),
            metadata.getLastTransaction().getPosition());

    saveState(newState);

    stateHistory.add(newState);
    stateRollbackCount.set(1);
  }

  void saveState(@NonNull final MysqlSourceState state) {
    stateRepository.save(state);
    lastSavedState.set(state);
  }

  MysqlSourceState getSavedState() {
    return Optional.ofNullable(stateRepository.read())
        .orElse(new MysqlSourceState(0L, 0L, currentLeaderEpoch.get(), initialBinlogFilePosition));
  }
}
