/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.airbnb.spinaltap.mysql.exception.InvalidBinlogPositionException;
import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaTracker;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.junit.Test;

public class AbstractMysqlSourceTest {
  private static final String SOURCE_NAME = "test";
  private static final DataSource DATA_SOURCE = new DataSource("test_host", 1, "test");
  private static final Set<String> TABLE_NAMES =
      Sets.newHashSet(Table.canonicalNameOf("db", "users"));

  private static final long SAVED_OFFSET = 12L;
  private static final long SAVED_TIMESTAMP = 12L;
  private static final BinlogFilePos BINLOG_FILE_POS = new BinlogFilePos("test.txt", 14, 100);

  private final TableCache tableCache = mock(TableCache.class);
  private final MysqlSourceMetrics mysqlMetrics = mock(MysqlSourceMetrics.class);
  private final StateRepository stateRepository = mock(StateRepository.class);
  private final AbstractMysqlSource.Listener listener = mock(AbstractMysqlSource.Listener.class);
  private final SchemaTracker schemaTracker = mock(MysqlSchemaTracker.class);

  @Test
  public void testOpenClose() throws Exception {
    TestSource source = new TestSource();
    SourceState savedState = new SourceState(SAVED_TIMESTAMP, SAVED_OFFSET, 0L, BINLOG_FILE_POS);

    when(stateRepository.read()).thenReturn(savedState);

    source.open();

    Transaction lastTransaction =
        new Transaction(
            savedState.getLastTimestamp(),
            savedState.getLastOffset(),
            savedState.getLastPosition());

    assertEquals(savedState, source.getLastSavedState().get());
    assertEquals(lastTransaction, source.getLastTransaction().get());
    assertEquals(BINLOG_FILE_POS, source.getPosition());
    verify(tableCache, times(1)).clear();
  }

  @Test
  public void testSaveState() throws Exception {
    TestSource source = new TestSource();
    SourceState savedState = mock(SourceState.class);
    SourceState newState = mock(SourceState.class);

    when(stateRepository.read()).thenReturn(savedState);

    source.saveState(newState);

    verify(stateRepository, times(1)).save(newState);
    assertEquals(newState, source.getLastSavedState().get());
  }

  @Test
  public void testGetState() throws Exception {
    TestSource source = new TestSource();
    SourceState savedState = mock(SourceState.class);

    when(stateRepository.read()).thenReturn(savedState);

    source.initialize();

    SourceState state = source.getSavedState();

    assertEquals(savedState, state);

    when(stateRepository.read()).thenReturn(null);

    state = source.getSavedState();

    assertEquals(0L, state.getLastOffset());
    assertEquals(0L, state.getLastTimestamp());
    assertEquals(AbstractMysqlSource.LATEST_BINLOG_POS, state.getLastPosition());
  }

  @Test
  public void testResetToLastValidState() throws Exception {
    StateHistory stateHistory = createTestStateHistory();
    TestSource source = new TestSource(stateHistory);

    SourceState savedState = mock(SourceState.class);
    SourceState earliestState =
        new SourceState(0L, 0L, 0L, AbstractMysqlSource.EARLIEST_BINLOG_POS);

    when(stateRepository.read()).thenReturn(savedState);

    SourceState firstState = mock(SourceState.class);
    SourceState secondState = mock(SourceState.class);
    SourceState thirdState = mock(SourceState.class);
    SourceState fourthState = mock(SourceState.class);

    stateHistory.add(firstState);
    stateHistory.add(secondState);
    stateHistory.add(thirdState);

    source.initialize();

    source.resetToLastValidState();
    assertEquals(thirdState, source.getLastSavedState().get());

    source.resetToLastValidState();
    assertEquals(firstState, source.getLastSavedState().get());
    assertTrue(stateHistory.isEmpty());

    source.resetToLastValidState();
    assertEquals(earliestState, source.getLastSavedState().get());

    stateHistory.add(firstState);
    stateHistory.add(secondState);
    stateHistory.add(thirdState);
    stateHistory.add(fourthState);

    source.resetToLastValidState();
    assertEquals(firstState, source.getLastSavedState().get());

    stateHistory.add(firstState);
    stateHistory.add(secondState);

    source.resetToLastValidState();
    assertEquals(earliestState, source.getLastSavedState().get());
    assertTrue(stateHistory.isEmpty());

    BinlogFilePos filePos = new BinlogFilePos("test.txt", 18, 156);
    Transaction lastTransaction = new Transaction(0L, 0L, filePos);
    MysqlMutationMetadata metadata =
        new MysqlMutationMetadata(null, null, null, 0L, 1L, 23L, null, lastTransaction, 0L, 0);

    source.checkpoint(new MysqlInsertMutation(metadata, null));

    assertFalse(stateHistory.isEmpty());

    source.resetToLastValidState();
    assertEquals(new SourceState(23L, 1L, 0L, filePos), source.getLastSavedState().get());
  }

  @Test
  public void testOnCommunicationError() throws Exception {
    TestSource source = new TestSource();

    source.addListener(listener);
    source.setPosition(null);

    source.onCommunicationError(new RuntimeException());
    assertNull(source.getLastSavedState().get());

    source.onCommunicationError(new InvalidBinlogPositionException(""));
    assertEquals(
        AbstractMysqlSource.EARLIEST_BINLOG_POS,
        source.getLastSavedState().get().getLastPosition());
  }

  @Test
  public void testCommitCheckpoint() throws Exception {
    StateHistory stateHistory = createTestStateHistory();
    TestSource source = new TestSource(stateHistory);

    Row row = new Row(null, ImmutableMap.of());
    BinlogFilePos filePos = new BinlogFilePos("test.txt", 18, 156);
    Transaction lastTransaction = new Transaction(0L, 0L, filePos);
    MysqlMutationMetadata metadata =
        new MysqlMutationMetadata(null, filePos, null, 0L, 0L, 0L, null, lastTransaction, 0, 0);
    MysqlMutation mutation = new MysqlInsertMutation(metadata, row);
    SourceState savedState = new SourceState(SAVED_TIMESTAMP, SAVED_OFFSET, 0L, BINLOG_FILE_POS);

    when(stateRepository.read()).thenReturn(savedState);

    source.initialize();

    source.checkpoint(mutation);
    assertEquals(savedState, source.getLastSavedState().get());

    source.checkpoint(null);
    assertEquals(savedState, source.getLastSavedState().get());

    long newOffset = SAVED_OFFSET + 1;
    metadata =
        new MysqlMutationMetadata(
            null, filePos, null, 0L, newOffset, 23L, null, lastTransaction, 0, 0);
    mutation = new MysqlInsertMutation(metadata, row);

    source.checkpoint(mutation);
    assertEquals(new SourceState(23L, newOffset, 0L, filePos), source.getLastSavedState().get());
    assertEquals(stateHistory.removeLast(), source.getLastSavedState().get());
  }

  private StateHistory createTestStateHistory() {
    return new StateHistory("", 10, mock(Repository.class), mysqlMetrics);
  }

  @Getter
  class TestSource extends AbstractMysqlSource {
    private boolean isConnected;
    private BinlogFilePos position;

    TestSource() {
      this(createTestStateHistory());
    }

    TestSource(StateHistory stateHistory) {
      super(
          SOURCE_NAME,
          DATA_SOURCE,
          TABLE_NAMES,
          tableCache,
          stateRepository,
          stateHistory,
          MysqlSource.LATEST_BINLOG_POS,
          schemaTracker,
          mysqlMetrics,
          new AtomicLong(0L),
          new AtomicReference<>(),
          new AtomicReference<>());
    }

    public void setPosition(BinlogFilePos pos) {
      position = pos;
    }

    public void connect() {
      isConnected = true;
    }

    public void disconnect() {
      isConnected = false;
    }

    public boolean isConnected() {
      return isConnected;
    }
  }
}
