/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlDeleteMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.MysqlUpdateMutation;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaTracker;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class MysqlMutationMapperTest {
  private static final DataSource DATA_SOURCE = new DataSource("test", 0, "test");
  private static final String DATABASE_NAME = "db";
  private static final String TABLE_NAME = "users";
  private static final byte[] COLUMN_TYPES = {0, 1};
  private static final long TABLE_ID = 0L;
  private static final long SERVER_ID = 1L;
  private static final long TIMESTAMP = 6L;
  private static BinlogFilePos BINLOG_FILE_POS = new BinlogFilePos("test.218", 14, 100);
  private static final Table TEST_TABLE =
      new Table(
          TABLE_ID,
          "Users",
          "test_db",
          null,
          ImmutableList.of(
              new ColumnMetadata("id", ColumnDataType.LONGLONG, true, 0),
              new ColumnMetadata("name", ColumnDataType.VARCHAR, false, 1),
              new ColumnMetadata("age", ColumnDataType.INT24, false, 2),
              new ColumnMetadata("sex", ColumnDataType.TINY, false, 3)),
          ImmutableList.of("id"));

  private final AtomicReference<Transaction> beginTransaction = new AtomicReference<>();
  private final AtomicReference<Transaction> lastTransaction = new AtomicReference<>();
  private final AtomicLong leaderEpoch = new AtomicLong(4l);

  private final TableCache tableCache = mock(TableCache.class);
  private final SchemaTracker schemaTracker = mock(MysqlSchemaTracker.class);
  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);

  private Mapper<BinlogEvent, List<? extends Mutation<?>>> eventMapper =
      MysqlMutationMapper.create(
          DATA_SOURCE,
          tableCache,
          schemaTracker,
          leaderEpoch,
          beginTransaction,
          lastTransaction,
          metrics);

  @Before
  public void setUp() throws Exception {
    lastTransaction.set(new Transaction(12L, 30L, new BinlogFilePos("test.txt", 14, 100)));
    beginTransaction.set(new Transaction(15L, 31L, new BinlogFilePos("test.txt", 14, 120)));

    when(tableCache.get(TABLE_ID)).thenReturn(TEST_TABLE);
  }

  @Test
  public void testInsertMutation() throws Exception {
    Serializable[] change = new Serializable[4];
    change[0] = 12131L;
    change[1] = "test_user";
    change[2] = 25;
    change[3] = 0;

    Serializable[] change2 = new Serializable[4];
    change2[0] = 12334L;
    change2[1] = "test_user2";
    change2[2] = 12;
    change2[3] = 1;

    BinlogEvent event =
        new WriteEvent(
            TABLE_ID, SERVER_ID, TIMESTAMP, BINLOG_FILE_POS, ImmutableList.of(change, change2));

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertEquals(2, mutations.size());

    assertTrue(mutations.get(0) instanceof MysqlInsertMutation);
    MysqlInsertMutation mutation = (MysqlInsertMutation) mutations.get(0);

    validateMetadata(mutation, 0);

    Row row = mutation.getEntity();

    assertEquals(12131L, row.getColumns().get("id").getValue());
    assertEquals("test_user", row.getColumns().get("name").getValue());
    assertEquals(25, row.getColumns().get("age").getValue());
    assertEquals(0, row.getColumns().get("sex").getValue());

    assertTrue(mutations.get(1) instanceof MysqlInsertMutation);
    mutation = (MysqlInsertMutation) mutations.get(1);

    validateMetadata(mutation, 1);

    row = mutation.getEntity();

    assertEquals(12334L, row.getColumns().get("id").getValue());
    assertEquals("test_user2", row.getColumns().get("name").getValue());
    assertEquals(12, row.getColumns().get("age").getValue());
    assertEquals(1, row.getColumns().get("sex").getValue());
  }

  @Test
  public void testUpdateMutation() throws Exception {
    Serializable[] old = new Serializable[4];
    old[0] = 12131L;
    old[1] = "test_user";
    old[2] = 25;
    old[3] = 0;

    Serializable[] current = new Serializable[4];
    current[0] = old[0];
    current[1] = old[1];
    current[2] = 26;
    current[3] = old[3];

    Serializable[] old2 = new Serializable[4];
    old2[0] = 12334L;
    old2[1] = "test_user2";
    old2[2] = 30;
    old2[3] = 1;

    Serializable[] current2 = new Serializable[4];
    current2[0] = old2[0];
    current2[1] = old2[1];
    current2[2] = 31;
    current2[3] = old2[3];

    Map.Entry<Serializable[], Serializable[]> change = new AbstractMap.SimpleEntry<>(old, current);
    Map.Entry<Serializable[], Serializable[]> change2 =
        new AbstractMap.SimpleEntry<>(old2, current2);

    BinlogEvent event =
        new UpdateEvent(
            TABLE_ID, SERVER_ID, TIMESTAMP, BINLOG_FILE_POS, ImmutableList.of(change, change2));

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertEquals(2, mutations.size());

    assertTrue(mutations.get(0) instanceof MysqlUpdateMutation);
    MysqlUpdateMutation mutation = (MysqlUpdateMutation) mutations.get(0);

    validateMetadata(mutation, 0);

    Row oldRow = mutation.getPreviousRow();
    Row newRow = mutation.getRow();

    assertEquals(12131L, oldRow.getColumns().get("id").getValue());
    assertEquals("test_user", oldRow.getColumns().get("name").getValue());
    assertEquals(25, oldRow.getColumns().get("age").getValue());
    assertEquals(0, oldRow.getColumns().get("sex").getValue());

    assertEquals(12131L, newRow.getColumns().get("id").getValue());
    assertEquals("test_user", newRow.getColumns().get("name").getValue());
    assertEquals(26, newRow.getColumns().get("age").getValue());
    assertEquals(0, newRow.getColumns().get("sex").getValue());

    assertTrue(mutations.get(1) instanceof MysqlUpdateMutation);
    mutation = (MysqlUpdateMutation) mutations.get(1);

    validateMetadata(mutation, 1);

    oldRow = mutation.getPreviousRow();
    newRow = mutation.getRow();

    assertEquals(12334L, oldRow.getColumns().get("id").getValue());
    assertEquals("test_user2", oldRow.getColumns().get("name").getValue());
    assertEquals(30, oldRow.getColumns().get("age").getValue());
    assertEquals(1, oldRow.getColumns().get("sex").getValue());

    assertEquals(12334L, newRow.getColumns().get("id").getValue());
    assertEquals("test_user2", newRow.getColumns().get("name").getValue());
    assertEquals(31, newRow.getColumns().get("age").getValue());
    assertEquals(1, newRow.getColumns().get("sex").getValue());
  }

  @Test
  public void testUpdateMutationWithDifferentPK() throws Exception {
    Serializable[] old = new Serializable[4];
    old[0] = 12131L;
    old[1] = "test_user";
    old[2] = 25;
    old[3] = 0;

    Serializable[] current = new Serializable[4];
    current[0] = 12334L;
    current[1] = old[1];
    current[2] = 26;
    current[3] = old[3];

    Map.Entry<Serializable[], Serializable[]> change = new AbstractMap.SimpleEntry<>(old, current);

    BinlogEvent event =
        new UpdateEvent(TABLE_ID, SERVER_ID, TIMESTAMP, BINLOG_FILE_POS, ImmutableList.of(change));

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertEquals(2, mutations.size());

    assertTrue(mutations.get(0) instanceof MysqlDeleteMutation);
    MysqlDeleteMutation deleteMutation = (MysqlDeleteMutation) mutations.get(0);

    validateMetadata(deleteMutation, 0);

    Row row = deleteMutation.getRow();

    assertEquals(12131L, row.getColumns().get("id").getValue());
    assertEquals("test_user", row.getColumns().get("name").getValue());
    assertEquals(25, row.getColumns().get("age").getValue());
    assertEquals(0, row.getColumns().get("sex").getValue());

    assertTrue(mutations.get(1) instanceof MysqlInsertMutation);
    MysqlInsertMutation insertMutation = (MysqlInsertMutation) mutations.get(1);

    validateMetadata(insertMutation, 0);

    row = insertMutation.getRow();

    assertEquals(12334L, row.getColumns().get("id").getValue());
    assertEquals("test_user", row.getColumns().get("name").getValue());
    assertEquals(26, row.getColumns().get("age").getValue());
    assertEquals(0, row.getColumns().get("sex").getValue());
  }

  @Test
  public void testUpdateMutationWithNullPK() throws Exception {
    Serializable[] old = new Serializable[4];
    old[0] = null;
    old[1] = "test_user";
    old[2] = 25;
    old[3] = 0;

    Serializable[] current = new Serializable[4];
    current[0] = null;
    current[1] = old[1];
    current[2] = 26;
    current[3] = old[3];

    Map.Entry<Serializable[], Serializable[]> change = new AbstractMap.SimpleEntry<>(old, current);

    BinlogEvent event =
        new UpdateEvent(TABLE_ID, SERVER_ID, TIMESTAMP, BINLOG_FILE_POS, ImmutableList.of(change));

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertEquals(1, mutations.size());

    assertTrue(mutations.get(0) instanceof MysqlUpdateMutation);
    MysqlUpdateMutation mutation = (MysqlUpdateMutation) mutations.get(0);

    validateMetadata(mutation, 0);

    Row oldRow = mutation.getPreviousRow();
    Row newRow = mutation.getRow();

    assertEquals(null, oldRow.getColumns().get("id").getValue());
    assertEquals("test_user", oldRow.getColumns().get("name").getValue());
    assertEquals(25, oldRow.getColumns().get("age").getValue());
    assertEquals(0, oldRow.getColumns().get("sex").getValue());

    assertEquals(null, newRow.getColumns().get("id").getValue());
    assertEquals("test_user", newRow.getColumns().get("name").getValue());
    assertEquals(26, newRow.getColumns().get("age").getValue());
    assertEquals(0, newRow.getColumns().get("sex").getValue());
  }

  @Test
  public void testDeleteMutation() throws Exception {
    Serializable[] change = new Serializable[4];
    change[0] = 12131L;
    change[1] = "test_user";
    change[2] = 25;
    change[3] = 0;

    Serializable[] change2 = new Serializable[4];
    change2[0] = 12334L;
    change2[1] = "test_user2";
    change2[2] = 12;
    change2[3] = 1;

    BinlogEvent event =
        new DeleteEvent(
            TABLE_ID, SERVER_ID, TIMESTAMP, BINLOG_FILE_POS, ImmutableList.of(change, change2));

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertEquals(2, mutations.size());

    assertTrue(mutations.get(0) instanceof MysqlDeleteMutation);
    MysqlDeleteMutation mutation = (MysqlDeleteMutation) mutations.get(0);

    validateMetadata(mutation, 0);

    Row row = mutation.getEntity();

    assertEquals(12131L, row.getColumns().get("id").getValue());
    assertEquals("test_user", row.getColumns().get("name").getValue());
    assertEquals(25, row.getColumns().get("age").getValue());
    assertEquals(0, row.getColumns().get("sex").getValue());

    assertTrue(mutations.get(1) instanceof MysqlDeleteMutation);
    mutation = (MysqlDeleteMutation) mutations.get(1);

    validateMetadata(mutation, 1);

    row = mutation.getEntity();

    assertEquals(12334L, row.getColumns().get("id").getValue());
    assertEquals("test_user2", row.getColumns().get("name").getValue());
    assertEquals(12, row.getColumns().get("age").getValue());
    assertEquals(1, row.getColumns().get("sex").getValue());
  }

  @Test
  public void testTableMap() throws Exception {
    TableMapEvent event =
        new TableMapEvent(
            TABLE_ID, 0l, 0l, BINLOG_FILE_POS, DATABASE_NAME, TABLE_NAME, COLUMN_TYPES);

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertTrue(mutations.isEmpty());
    verify(tableCache, times(1))
        .addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, BINLOG_FILE_POS, event.getColumnTypes());
  }

  @Test
  public void testXid() throws Exception {
    XidEvent xidEvent = new XidEvent(SERVER_ID, 15l, new BinlogFilePos("test.200", 18, 130), 0l);

    List<? extends Mutation> mutations = eventMapper.map(xidEvent);

    assertTrue(mutations.isEmpty());

    assertEquals(15L, lastTransaction.get().getTimestamp());

    verify(metrics, times(1)).transactionReceived();
  }

  @Test
  public void testQuery() throws Exception {
    QueryEvent queryEvent = new QueryEvent(SERVER_ID, 15l, BINLOG_FILE_POS, DATABASE_NAME, "BEGIN");

    List<? extends Mutation> mutations = eventMapper.map(queryEvent);

    assertTrue(mutations.isEmpty());
    assertEquals(15L, beginTransaction.get().getTimestamp());

    queryEvent = new QueryEvent(SERVER_ID, 30l, BINLOG_FILE_POS, DATABASE_NAME, "");
    mutations = eventMapper.map(queryEvent);

    assertTrue(mutations.isEmpty());
    assertEquals(15L, beginTransaction.get().getTimestamp());

    queryEvent = new QueryEvent(SERVER_ID, 30l, BINLOG_FILE_POS, DATABASE_NAME, "BEGIN");
    mutations = eventMapper.map(queryEvent);

    assertTrue(mutations.isEmpty());
    assertEquals(30L, beginTransaction.get().getTimestamp());
  }

  @Test
  public void testStart() throws Exception {
    StartEvent event = new StartEvent(0l, 0l, BINLOG_FILE_POS);

    List<? extends Mutation> mutations = eventMapper.map(event);

    assertTrue(mutations.isEmpty());
    verify(tableCache, times(1)).clear();
  }

  @Test(expected = IllegalStateException.class)
  public void testNoMutationMapping() throws Exception {
    eventMapper.map(mock(BinlogEvent.class));
  }

  private void validateMetadata(Mutation mutation, int rowPosition) {
    MysqlMutationMetadata metadata = (MysqlMutationMetadata) mutation.getMetadata();

    assertEquals(DATA_SOURCE, metadata.getDataSource());
    assertEquals(BINLOG_FILE_POS, metadata.getFilePos());
    assertEquals(TEST_TABLE, metadata.getTable());
    assertEquals(SERVER_ID, metadata.getServerId());
    assertEquals(TIMESTAMP, metadata.getTimestamp());
    assertEquals(beginTransaction.get(), metadata.getBeginTransaction());
    assertEquals(lastTransaction.get(), metadata.getLastTransaction());
    assertEquals(leaderEpoch.get(), metadata.getLeaderEpoch());
    assertEquals(rowPosition, metadata.getEventRowPosition());
  }
}
