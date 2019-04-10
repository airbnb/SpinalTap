/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.binlog_connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.FormatDescriptionEventData;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;
import com.github.shyiko.mysql.binlog.event.XAPrepareEventData;
import com.github.shyiko.mysql.binlog.event.XidEventData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.Serializable;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class BinaryLogConnectorEventMapperTest {
  private static final long TABLE_ID = 888L;
  private static final long SERVER_ID = 65535L;
  private static final long TIMESTAMP = 100000L;
  private static final String DATABASE = "db";
  private static final String TABLE = "table";
  private static final BinlogFilePos BINLOG_FILE_POS = new BinlogFilePos(1000);
  private static final Serializable[] ROW = new Integer[] {1, 2, 3, 4};
  private static final Serializable[] PREV_ROW = new Integer[] {4, 3, 1, 2};
  private EventHeaderV4 eventHeader = new EventHeaderV4();

  @Before
  public void setUp() {
    eventHeader.setEventLength(123L);
    eventHeader.setServerId(SERVER_ID);
    eventHeader.setNextPosition(100L);
    eventHeader.setTimestamp(TIMESTAMP);
  }

  @Test
  public void testWriteEvent() {
    eventHeader.setEventType(EventType.EXT_WRITE_ROWS);
    WriteRowsEventData eventData = new WriteRowsEventData();
    eventData.setTableId(TABLE_ID);
    eventData.setRows(ImmutableList.of(ROW));

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof WriteEvent);
    WriteEvent writeEvent = (WriteEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, writeEvent.getBinlogFilePos());
    assertEquals(ImmutableList.of(ROW), writeEvent.getRows());
    assertEquals(SERVER_ID, writeEvent.getServerId());
    assertEquals(TABLE_ID, writeEvent.getTableId());
    assertEquals(TIMESTAMP, writeEvent.getTimestamp());
  }

  @Test
  public void testUpdateEvent() {
    eventHeader.setEventType(EventType.EXT_UPDATE_ROWS);
    UpdateRowsEventData eventData = new UpdateRowsEventData();
    eventData.setTableId(TABLE_ID);
    eventData.setRows(ImmutableList.of(Maps.immutableEntry(PREV_ROW, ROW)));

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof UpdateEvent);
    UpdateEvent updateEvent = (UpdateEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, updateEvent.getBinlogFilePos());
    assertEquals(ImmutableList.of(Maps.immutableEntry(PREV_ROW, ROW)), updateEvent.getRows());
    assertEquals(SERVER_ID, updateEvent.getServerId());
    assertEquals(TABLE_ID, updateEvent.getTableId());
    assertEquals(TIMESTAMP, updateEvent.getTimestamp());
  }

  @Test
  public void testDeleteEvent() {
    eventHeader.setEventType(EventType.EXT_DELETE_ROWS);
    DeleteRowsEventData eventData = new DeleteRowsEventData();
    eventData.setTableId(TABLE_ID);
    eventData.setRows(ImmutableList.of(PREV_ROW));

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof DeleteEvent);
    DeleteEvent deleteEvent = (DeleteEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, deleteEvent.getBinlogFilePos());
    assertEquals(ImmutableList.of(PREV_ROW), deleteEvent.getRows());
    assertEquals(SERVER_ID, deleteEvent.getServerId());
    assertEquals(TABLE_ID, deleteEvent.getTableId());
    assertEquals(TIMESTAMP, deleteEvent.getTimestamp());
  }

  @Test
  public void testTableMapEvent() {
    eventHeader.setEventType(EventType.TABLE_MAP);
    TableMapEventData eventData = new TableMapEventData();
    eventData.setDatabase(DATABASE);
    eventData.setTable(TABLE);
    eventData.setTableId(TABLE_ID);
    eventData.setColumnTypes(new byte[] {(byte) 0, (byte) 1, (byte) 2});

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof TableMapEvent);
    TableMapEvent tableMapEvent = (TableMapEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, tableMapEvent.getBinlogFilePos());
    assertEquals(DATABASE, tableMapEvent.getDatabase());
    assertEquals(TABLE, tableMapEvent.getTable());
    assertEquals(TABLE_ID, tableMapEvent.getTableId());
    assertEquals(
        ImmutableList.of(ColumnDataType.DECIMAL, ColumnDataType.TINY, ColumnDataType.SHORT),
        tableMapEvent.getColumnTypes());
  }

  @Test
  public void testXidEvent() {
    long xid = 88888L;
    eventHeader.setEventType(EventType.XID);
    XidEventData eventData = new XidEventData();
    eventData.setXid(xid);

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof XidEvent);
    XidEvent xidEvent = (XidEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, xidEvent.getBinlogFilePos());
    assertEquals(SERVER_ID, xidEvent.getServerId());
    assertEquals(TIMESTAMP, xidEvent.getTimestamp());
    assertEquals(xid, xidEvent.getXid());
  }

  @Test
  public void testQueryEvent() {
    String sql = "CREATE UNIQUE INDEX unique_index ON `my_db`.`my_table` (`col1`, `col2`)";
    String sql_with_comments =
        "CREATE/* ! COMMENTS ! */UNIQUE /* ANOTHER COMMENTS ! */INDEX unique_index\n"
            + "ON `my_db`.`my_table` (`col1`, `col2`)";
    eventHeader.setEventType(EventType.QUERY);
    QueryEventData eventData = new QueryEventData();
    eventData.setDatabase(DATABASE);
    eventData.setSql(sql);

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof QueryEvent);
    QueryEvent queryEvent = (QueryEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, queryEvent.getBinlogFilePos());
    assertEquals(DATABASE, queryEvent.getDatabase());
    assertEquals(SERVER_ID, queryEvent.getServerId());
    assertEquals(TIMESTAMP, queryEvent.getTimestamp());
    assertEquals(sql, queryEvent.getSql());

    eventData.setSql(sql_with_comments);
    binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    String expected_sql =
        "CREATE UNIQUE  INDEX unique_index ON `my_db`.`my_table` (`col1`, `col2`)";
    String stripped_sql = ((QueryEvent) (binlogEvent.get())).getSql();
    assertEquals(expected_sql, stripped_sql);
  }

  @Test
  public void testFormatDescriptionEvent() {
    eventHeader.setEventType(EventType.FORMAT_DESCRIPTION);
    FormatDescriptionEventData eventData = new FormatDescriptionEventData();

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertTrue(binlogEvent.isPresent());
    assertTrue(binlogEvent.get() instanceof StartEvent);
    StartEvent startEvent = (StartEvent) (binlogEvent.get());
    assertEquals(BINLOG_FILE_POS, startEvent.getBinlogFilePos());
    assertEquals(SERVER_ID, startEvent.getServerId());
    assertEquals(TIMESTAMP, startEvent.getTimestamp());
  }

  @Test
  public void testIgnoredEvents() {
    eventHeader.setEventType(EventType.UNKNOWN);
    XAPrepareEventData eventData = new XAPrepareEventData();

    Optional<BinlogEvent> binlogEvent =
        BinaryLogConnectorEventMapper.INSTANCE.map(
            new Event(eventHeader, eventData), BINLOG_FILE_POS);
    assertFalse(binlogEvent.isPresent());
  }
}
