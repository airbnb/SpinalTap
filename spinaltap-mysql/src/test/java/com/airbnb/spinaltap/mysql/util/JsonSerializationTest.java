/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.util;

import static org.junit.Assert.*;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.JsonUtil;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.DataSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Queues;
import java.util.Collection;
import java.util.Deque;
import org.junit.Test;

public class JsonSerializationTest {
  private static final DataSource DATA_SOURCE = new DataSource("test", 3306, "test_service");
  private static final BinlogFilePos BINLOG_FILE_POS = new BinlogFilePos("test.218", 1234, 5678);
  private static final SourceState SOURCE_STATE = new SourceState(15l, 20l, -1l, BINLOG_FILE_POS);

  @Test
  public void testSerializeDataSource() throws Exception {
    assertEquals(
        DATA_SOURCE,
        JsonUtil.OBJECT_MAPPER.readValue(
            JsonUtil.OBJECT_MAPPER.writeValueAsString(DATA_SOURCE), DataSource.class));
  }

  @Test
  public void testSerializeBinlogFilePos() throws Exception {
    assertEquals(
        BINLOG_FILE_POS,
        JsonUtil.OBJECT_MAPPER.readValue(
            JsonUtil.OBJECT_MAPPER.writeValueAsString(BINLOG_FILE_POS),
            new TypeReference<BinlogFilePos>() {}));
  }

  @Test
  public void testSerializeSourceState() throws Exception {
    SourceState state =
        JsonUtil.OBJECT_MAPPER.readValue(
            JsonUtil.OBJECT_MAPPER.writeValueAsString(SOURCE_STATE),
            new TypeReference<SourceState>() {});

    assertEquals(BINLOG_FILE_POS, state.getLastPosition());
    assertEquals(SOURCE_STATE.getLastTimestamp(), state.getLastTimestamp());
    assertEquals(SOURCE_STATE.getLastOffset(), state.getLastOffset());
  }

  @Test
  public void testSerializeStateHistory() throws Exception {
    SourceState firstState = new SourceState(15l, 20l, -1l, BINLOG_FILE_POS);
    SourceState secondState = new SourceState(16l, 21l, -1l, BINLOG_FILE_POS);
    SourceState thirdState = new SourceState(17l, 22l, -1l, BINLOG_FILE_POS);

    Deque<SourceState> stateHistory = Queues.newArrayDeque();
    stateHistory.addLast(firstState);
    stateHistory.addLast(secondState);
    stateHistory.addLast(thirdState);

    Collection<SourceState> states =
        JsonUtil.OBJECT_MAPPER.readValue(
            JsonUtil.OBJECT_MAPPER.writeValueAsString(stateHistory),
            new TypeReference<Collection<SourceState>>() {});

    stateHistory = Queues.newArrayDeque(states);

    assertEquals(3, states.size());
    assertEquals(thirdState, stateHistory.removeLast());
    assertEquals(secondState, stateHistory.removeLast());
    assertEquals(firstState, stateHistory.removeLast());
  }
}
