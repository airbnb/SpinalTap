/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlClient;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MysqlSchemaManagerTest {
  private static final String GTID = "fec1aada-c5fc-11e9-9af8-0242ac110003:1002";
  private static final long TIMESTAMP = 7849733221L;
  private static final BinlogFilePos BINLOG_FILE_POS =
      BinlogFilePos.fromString("mysql-binlog.001233:345:456");
  private static final List<MysqlColumn> TABLE1_COLUMNS =
      Collections.singletonList(new MysqlColumn("id", "int", "int", false));
  private static final List<MysqlColumn> TABLE2_COLUMNS =
      Arrays.asList(
          new MysqlColumn("id", "int", "int", true),
          new MysqlColumn("name", "varchar", "varchar(255)", false));
  private static final List<MysqlColumn> TABLE3_COLUMNS =
      Arrays.asList(
          new MysqlColumn("id", "int", "int", true),
          new MysqlColumn("name", "text", "text", false),
          new MysqlColumn("phone", "varchar(255)", "varchar", false));

  private static final MysqlTableSchema TABLE1_SCHEMA =
      new MysqlTableSchema(
          1,
          "db1",
          "table1",
          BINLOG_FILE_POS,
          GTID,
          "CREATE TABLE table1 (id INT)",
          192000,
          TABLE1_COLUMNS,
          Collections.emptyMap());
  private static final MysqlTableSchema TABLE2_SCHEMA =
      new MysqlTableSchema(
          2,
          "db1",
          "table2",
          BINLOG_FILE_POS,
          GTID,
          "CREATE TABLE table2 (id INT, name VARCHAR(255))",
          492000,
          TABLE2_COLUMNS,
          Collections.emptyMap());
  private static final MysqlTableSchema TABLE3_SCHEMA =
      new MysqlTableSchema(
          3,
          "db2",
          "table3",
          BINLOG_FILE_POS,
          GTID,
          "ALTER TABLE db2.table3 ADD name text, ADD phone VARCHAR(255)",
          892000,
          TABLE3_COLUMNS,
          Collections.emptyMap());

  private static final Table<String, String, MysqlTableSchema> SCHEMA_STORE_CACHE =
      Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
  private final MysqlSchemaStore schemaStore = mock(MysqlSchemaStore.class);
  private final MysqlSchemaDatabase schemaDatabase = mock(MysqlSchemaDatabase.class);
  private final MysqlSchemaReader schemaReader = mock(MysqlSchemaReader.class);
  private final MysqlClient mysqlClient = mock(MysqlClient.class);
  private final MysqlSchemaManager schemaManager =
      new MysqlSchemaManager(
          "test_source", schemaStore, schemaDatabase, schemaReader, mysqlClient, true);

  static {
    SCHEMA_STORE_CACHE.put("db1", "table1", TABLE1_SCHEMA);
    SCHEMA_STORE_CACHE.put("db1", "table2", TABLE2_SCHEMA);
    SCHEMA_STORE_CACHE.put("db2", "table3", TABLE3_SCHEMA);
  }

  @Before
  public void setUp() {
    when(schemaDatabase.listDatabases()).thenReturn(ImmutableList.of("db1", "db2"));
    when(schemaDatabase.getColumnsForAllTables("db1"))
        .thenReturn(ImmutableMap.of("table1", TABLE1_COLUMNS, "table2", TABLE2_COLUMNS));
    when(schemaDatabase.getColumnsForAllTables("db2"))
        .thenReturn(ImmutableMap.of("table3", TABLE3_COLUMNS));

    when(schemaStore.getSchemaCache()).thenReturn(SCHEMA_STORE_CACHE);
  }

  @Test
  public void testSchemaVersionDisabled() {
    final MysqlSchemaManager schemaManagerWithSchemaVersionDisabled =
        new MysqlSchemaManager("test_source", null, null, schemaReader, mysqlClient, false);
    when(schemaReader.getTableColumns("db", "table")).thenReturn(TABLE1_COLUMNS);
    List<MysqlColumn> columns =
        schemaManagerWithSchemaVersionDisabled.getTableColumns("db", "table");
    assertEquals(TABLE1_COLUMNS, columns);
    verify(schemaReader).getTableColumns("db", "table");
  }

  @Test
  public void testCreateTable() {
    String sql = "CREATE TABLE table123 (uid INT)";
    QueryEvent event = new QueryEvent(1L, TIMESTAMP, BINLOG_FILE_POS, "db1", sql);
    List<MysqlColumn> newTableColumns =
        Collections.singletonList(new MysqlColumn("uid", "int", "int", false));
    when(schemaDatabase.getColumnsForAllTables("db1"))
        .thenReturn(
            ImmutableMap.of(
                "table1", TABLE1_COLUMNS, "table2", TABLE2_COLUMNS, "table123", newTableColumns));

    schemaManager.processDDL(event, GTID);
    verify(schemaDatabase).applyDDL(sql, "db1");
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db1",
                "table123",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                newTableColumns,
                Collections.emptyMap()));
  }

  @Test
  public void testDropTable() {
    String sql = "DROP TABLE db1.table2";
    QueryEvent event = new QueryEvent(1L, TIMESTAMP, BINLOG_FILE_POS, "db1", sql);
    when(schemaDatabase.getColumnsForAllTables("db1"))
        .thenReturn(ImmutableMap.of("table1", TABLE1_COLUMNS));

    schemaManager.processDDL(event, GTID);
    verify(schemaDatabase).applyDDL(sql, "db1");
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db1",
                "table2",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                Collections.emptyList(),
                Collections.emptyMap()));
  }

  @Test
  public void testAlterTable() {
    String sql = "ALTER TABLE db1.table1 ADD account VARCHAR(255)";
    QueryEvent event = new QueryEvent(1L, TIMESTAMP, BINLOG_FILE_POS, "db1", sql);
    List<MysqlColumn> newTableColumns =
        Arrays.asList(
            new MysqlColumn("id", "int", "int", false),
            new MysqlColumn("account", "varchar", "varchar(255)", false));
    when(schemaDatabase.getColumnsForAllTables("db1"))
        .thenReturn(ImmutableMap.of("table1", newTableColumns, "table2", TABLE2_COLUMNS));

    schemaManager.processDDL(event, GTID);
    verify(schemaDatabase).applyDDL(sql, "db1");
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db1",
                "table1",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                newTableColumns,
                Collections.emptyMap()));
  }

  @Test
  public void testRenameTable() {
    String sql = "RENAME TABLE db2.table3 TO db2.table4";
    QueryEvent event = new QueryEvent(1L, TIMESTAMP, BINLOG_FILE_POS, "db2", sql);
    when(schemaDatabase.getColumnsForAllTables("db2"))
        .thenReturn(ImmutableMap.of("table4", TABLE3_COLUMNS));

    schemaManager.processDDL(event, GTID);
    verify(schemaDatabase).applyDDL(sql, "db2");
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db2",
                "table3",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                Collections.emptyList(),
                Collections.emptyMap()));
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db2",
                "table4",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                TABLE3_COLUMNS,
                Collections.emptyMap()));
  }

  @Test
  public void testCreateDatabase() {
    String sql = "CREATE DATABASE db3";
    QueryEvent event = new QueryEvent(1L, TIMESTAMP, BINLOG_FILE_POS, "db3", sql);
    when(schemaDatabase.listDatabases()).thenReturn(ImmutableList.of("db1", "db2", "db3"));
    when(schemaDatabase.getColumnsForAllTables("db3")).thenReturn(Collections.emptyMap());

    schemaManager.processDDL(event, GTID);
    verify(schemaDatabase).applyDDL(sql, null);
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db3",
                null,
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                Collections.emptyList(),
                Collections.emptyMap()));
  }

  @Test
  public void testDropDatabase() {
    String sql = "DROP DATABASE db1";
    QueryEvent event = new QueryEvent(1, TIMESTAMP, BINLOG_FILE_POS, null, sql);
    when(schemaDatabase.listDatabases()).thenReturn(ImmutableList.of("db2"));
    when(schemaDatabase.getColumnsForAllTables("db1")).thenReturn(Collections.emptyMap());

    schemaManager.processDDL(event, GTID);
    verify(schemaDatabase).applyDDL(sql, null);
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db1",
                "table1",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                Collections.emptyList(),
                Collections.emptyMap()));
    verify(schemaStore)
        .put(
            new MysqlTableSchema(
                0,
                "db1",
                "table2",
                BINLOG_FILE_POS,
                GTID,
                sql,
                TIMESTAMP,
                Collections.emptyList(),
                Collections.emptyMap()));
  }
}
