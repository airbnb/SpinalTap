/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;

public class CachedMysqlSchemaStoreTest {
  private static final String SOURCE_NAME = "source";
  private static final String DATABASE_NAME = "db";
  private static final MysqlSchemaStore MYSQL_SCHEMA_STORE = mock(MysqlSchemaStore.class);

  private static final List<ColumnInfo> TABLE1_COLUMN_LIST_VER1 =
      Arrays.asList(
          new ColumnInfo("table1", "col1", "TINY", true),
          new ColumnInfo("table1", "col2", "STRING", false),
          new ColumnInfo("table1", "col3", "FLOAT", true),
          new ColumnInfo("table1", "col4", "LONG", false));

  private static final List<ColumnInfo> TABLE1_COLUMN_LIST_VER2 =
      Arrays.asList(
          new ColumnInfo("table1", "col1", "TINY", true),
          new ColumnInfo("table1", "col2", "STRING", false),
          new ColumnInfo("table1", "col3", "DOUBLE", true),
          new ColumnInfo("table1", "col4", "LONG", false));

  private static final List<ColumnInfo> TABLE1_COLUMN_LIST_VER3 =
      Arrays.asList(
          new ColumnInfo("table1", "col1", "TINY", true),
          new ColumnInfo("table1", "col2", "STRING", false),
          new ColumnInfo("table1", "col3", "DOUBLE", true));

  private static final List<ColumnInfo> TABLE2_COLUMN_LIST_VER1 =
      Arrays.asList(
          new ColumnInfo("table2", "col1", "INT", true),
          new ColumnInfo("table2", "col2", "TEXT", false),
          new ColumnInfo("table2", "col3", "VARCHAR", true));

  private static final List<ColumnInfo> TABLE2_COLUMN_LIST_VER2 =
      Arrays.asList(
          new ColumnInfo("table2", "col1", "INT", true),
          new ColumnInfo("table2", "col2", "TEXT", false),
          new ColumnInfo("table2", "col3", "VARCHAR", true),
          new ColumnInfo("table2", "col4", "FLOAT", true));

  private static final BinlogFilePos TABLE1_BINLOG_POSITION_VER1 =
      new BinlogFilePos(555, 1000, 1050);
  private static final BinlogFilePos TABLE1_BINLOG_POSITION_VER2 = new BinlogFilePos(557, 244, 308);
  private static final BinlogFilePos TABLE1_BINLOG_POSITION_VER3 =
      new BinlogFilePos(558, 2322, 2500);
  private static final BinlogFilePos TABLE2_BINLOG_POSITION_VER1 =
      new BinlogFilePos(8322, 79923, 80122);
  private static final BinlogFilePos TABLE2_BINLOG_POSITION_VER2 =
      new BinlogFilePos(8325, 29931, 30012);

  private static final String DDL = "ALTER TABLE";

  private final MysqlTableSchema table1Version1 =
      new MysqlTableSchema(
          1,
          SOURCE_NAME,
          DATABASE_NAME,
          "table1",
          TABLE1_BINLOG_POSITION_VER1,
          DDL,
          1000,
          TABLE1_COLUMN_LIST_VER1,
          null);
  private final MysqlTableSchema table1Version2 =
      new MysqlTableSchema(
          2,
          SOURCE_NAME,
          DATABASE_NAME,
          "table1",
          TABLE1_BINLOG_POSITION_VER2,
          DDL,
          2000,
          TABLE1_COLUMN_LIST_VER2,
          null);
  private final MysqlTableSchema table1Version3 =
      new MysqlTableSchema(
          3,
          SOURCE_NAME,
          DATABASE_NAME,
          "table1",
          TABLE1_BINLOG_POSITION_VER3,
          DDL,
          3000,
          TABLE1_COLUMN_LIST_VER3,
          null);

  private final MysqlTableSchema table2Version1 =
      new MysqlTableSchema(
          1,
          SOURCE_NAME,
          DATABASE_NAME,
          "table2",
          TABLE2_BINLOG_POSITION_VER1,
          DDL,
          12345,
          TABLE2_COLUMN_LIST_VER1,
          null);
  private final MysqlTableSchema table2Version2 =
      new MysqlTableSchema(
          2,
          SOURCE_NAME,
          DATABASE_NAME,
          "table2",
          TABLE2_BINLOG_POSITION_VER2,
          DDL,
          20020,
          TABLE2_COLUMN_LIST_VER2,
          null);

  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);
  private SchemaStore<MysqlTableSchema> cachedMysqlSchemaStore;

  @Before
  public void setUp() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> initialSchema =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    initialSchema.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, table1Version1);
          }
        });
    initialSchema.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, table2Version1);
          }
        });
    when(MYSQL_SCHEMA_STORE.getAll()).thenReturn(initialSchema);

    cachedMysqlSchemaStore = new CachedMysqlSchemaStore(SOURCE_NAME, MYSQL_SCHEMA_STORE, metrics);

    cachedMysqlSchemaStore.put(
        DATABASE_NAME, "table1", TABLE1_BINLOG_POSITION_VER2, 2000, DDL, TABLE1_COLUMN_LIST_VER2);
    cachedMysqlSchemaStore.put(
        DATABASE_NAME, "table1", TABLE1_BINLOG_POSITION_VER3, 3000, DDL, TABLE1_COLUMN_LIST_VER3);
    cachedMysqlSchemaStore.put(
        DATABASE_NAME, "table2", TABLE2_BINLOG_POSITION_VER2, 20020, DDL, TABLE2_COLUMN_LIST_VER2);
  }

  @Test
  public void testGetByVersion() throws Exception {
    assertEquals(table1Version1, cachedMysqlSchemaStore.get(DATABASE_NAME, "table1", 1));
    assertEquals(table1Version3, cachedMysqlSchemaStore.get(DATABASE_NAME, "table1", 3));
    assertEquals(table2Version1, cachedMysqlSchemaStore.get(DATABASE_NAME, "table2", 1));
    assertEquals(table2Version2, cachedMysqlSchemaStore.get(DATABASE_NAME, "table2", 2));
  }

  @Test
  public void testQueryByBinlogPosition() throws Exception {
    assertEquals(
        table1Version1,
        cachedMysqlSchemaStore.query(
            DATABASE_NAME, "table1", new BinlogFilePos("556", 80000, 80010)));
    assertEquals(
        table1Version2,
        cachedMysqlSchemaStore.query(
            DATABASE_NAME, "table1", new BinlogFilePos("557", 78392, 79032)));
    assertEquals(
        table2Version2,
        cachedMysqlSchemaStore.query(
            DATABASE_NAME, "table2", new BinlogFilePos("9000", 1002, 1690)));
  }

  @Test
  public void testGetByBinlogPosition() throws Exception {
    assertEquals(table1Version1, cachedMysqlSchemaStore.get(TABLE1_BINLOG_POSITION_VER1));
    assertEquals(table1Version2, cachedMysqlSchemaStore.get(TABLE1_BINLOG_POSITION_VER2));
    assertEquals(table1Version3, cachedMysqlSchemaStore.get(TABLE1_BINLOG_POSITION_VER3));
    assertEquals(table2Version1, cachedMysqlSchemaStore.get(TABLE2_BINLOG_POSITION_VER1));
    assertEquals(table2Version2, cachedMysqlSchemaStore.get(TABLE2_BINLOG_POSITION_VER2));
    assertNull(cachedMysqlSchemaStore.get(new BinlogFilePos("123", 456, 789)));
  }

  @Test
  public void testGetLatestByTable() throws Exception {
    assertEquals(table1Version3, cachedMysqlSchemaStore.getLatest(DATABASE_NAME, "table1"));
    assertEquals(table2Version2, cachedMysqlSchemaStore.getLatest(DATABASE_NAME, "table2"));
  }

  @Test
  public void testGetLatestByDatabase() throws Exception {
    Map<String, MysqlTableSchema> latestSchemaMap =
        ImmutableMap.of("table1", table1Version3, "table2", table2Version2);
    assertEquals(latestSchemaMap, cachedMysqlSchemaStore.getLatest(DATABASE_NAME));
  }

  @Test
  public void testGetLatestVersion() throws Exception {
    assertEquals(3, cachedMysqlSchemaStore.getLatestVersion(DATABASE_NAME, "table1"));
    assertEquals(2, cachedMysqlSchemaStore.getLatestVersion(DATABASE_NAME, "table2"));
  }

  @Test
  public void testGetByInvalidBinlogPosition() throws Exception {
    assertNull(cachedMysqlSchemaStore.get(new BinlogFilePos(557, 1200, 1250)));
  }

  @Test(expected = RuntimeException.class)
  public void testGetByInvalidVersion() throws RuntimeException {
    try {
      cachedMysqlSchemaStore.get(DATABASE_NAME, "table1", 4);
    } catch (RuntimeException ex) {
      throw ex;
    }
    fail("Runtime exception expected.");
  }

  @Test(expected = RuntimeException.class)
  public void testGetByInvalidTable() throws RuntimeException {
    try {
      cachedMysqlSchemaStore.get(DATABASE_NAME, "table3", 1);
    } catch (RuntimeException ex) {
      throw ex;
    }
    fail("Runtime exception expected.");
  }

  @Test(expected = RuntimeException.class)
  public void testQueryByInvalidBinlogPosition() throws RuntimeException {
    try {
      cachedMysqlSchemaStore.query(DATABASE_NAME, "table1", new BinlogFilePos(1, 2, 4));
    } catch (RuntimeException ex) {
      throw ex;
    }
    fail("Runtime exception expected.");
  }
}
