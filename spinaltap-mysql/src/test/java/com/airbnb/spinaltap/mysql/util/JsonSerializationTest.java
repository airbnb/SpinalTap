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
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.shyiko.mysql.binlog.network.SSLMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import java.util.Collection;
import java.util.Deque;
import org.junit.Test;

public class JsonSerializationTest {
  private static final DataSource DATA_SOURCE = new DataSource("test", 3306, "test_service");
  private static final BinlogFilePos BINLOG_FILE_POS = new BinlogFilePos("test.218", 1234, 5678);
  private static final SourceState SOURCE_STATE = new SourceState(15l, 20l, -1l, BINLOG_FILE_POS);
  private static final String SERVER_UUID = "4a4ac150-fe5b-4093-a1ef-a8876011adaa";

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
  public void testSerializeBinlogFilePosWithGTID() throws Exception {
    BinlogFilePos pos =
        new BinlogFilePos("test.123", 123, 456, SERVER_UUID + ":1-123", SERVER_UUID);
    assertEquals(
        pos,
        JsonUtil.OBJECT_MAPPER.readValue(
            JsonUtil.OBJECT_MAPPER.writeValueAsString(pos), new TypeReference<BinlogFilePos>() {}));
  }

  @Test
  public void testDeserialzeBinlogFilePosWithoutGTID() throws Exception {
    String jsonString = "{\"fileName\": \"test.123\", \"position\": 4, \"nextPosition\": 8}";
    BinlogFilePos pos =
        JsonUtil.OBJECT_MAPPER.readValue(jsonString, new TypeReference<BinlogFilePos>() {});
    assertEquals("test.123", pos.getFileName());
    assertEquals(123, pos.getFileNumber());
    assertEquals(4, pos.getPosition());
    assertEquals(8, pos.getNextPosition());
    assertNull(pos.getServerUUID());
    assertNull(pos.getGtidSet());
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

  @Test
  public void testDeserializeMysqlConfiguration() throws Exception {
    String configYaml =
        "name: test\n"
            + "host: localhost\n"
            + "port: 3306\n"
            + "tables:\n"
            + "  - test_db:test_table\n"
            + "  - test_db:test_table2\n"
            + "socket_timeout_seconds: -1\n"
            + "ssl_mode: REQUIRED\n"
            + "mtls_enabled: true\n"
            + "destination:\n"
            + "  pool_size: 5\n"
            + "  buffer_size: 1000\n";
    MysqlConfiguration config =
        new ObjectMapper(new YAMLFactory()).readValue(configYaml, MysqlConfiguration.class);

    assertEquals("test", config.getName());
    assertEquals("localhost", config.getHost());
    assertEquals(3306, config.getPort());
    assertEquals(
        ImmutableList.of("test_db:test_table", "test_db:test_table2"),
        config.getCanonicalTableNames());
    assertEquals(-1, config.getSocketTimeoutInSeconds());
    assertEquals(1000, config.getDestinationConfiguration().getBufferSize());
    assertEquals(5, config.getDestinationConfiguration().getPoolSize());
    assertEquals(SSLMode.REQUIRED, config.getSslMode());
    assertTrue(config.isMTlsEnabled());
  }
}
