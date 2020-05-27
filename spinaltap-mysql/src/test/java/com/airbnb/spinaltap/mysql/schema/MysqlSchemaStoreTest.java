/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.junit.Before;
import org.junit.Test;

public class MysqlSchemaStoreTest {
  private static final String SERVER_UUID_1 = "fec1aada-c5fc-11e9-9af8-0242ac110003";
  private static final String SERVER_UUID_2 = "3a7e553e-6ae1-11ea-99ee-0242ac110005";
  private static final List<MysqlColumn> TABLE1_COLUMNS =
      Collections.singletonList(new MysqlColumn("id", "int", "int", false));
  private static final List<MysqlColumn> TABLE2_COLUMNS =
      Arrays.asList(
          new MysqlColumn("id", "int", "int", true),
          new MysqlColumn("name", "varchar", "varchar(255)", false));
  private static final List<MysqlColumn> TABLE1_COLUMNS_NEW =
      Arrays.asList(
          new MysqlColumn("id", "int", "int", true),
          new MysqlColumn("name", "text", "text", false),
          new MysqlColumn("phone", "varchar(255)", "varchar", false));

  private static final BinlogFilePos FIRST_POS =
      new BinlogFilePos("mysql-binlog.001394", 6, 12, SERVER_UUID_1 + ":1-24", SERVER_UUID_1);
  private static final MysqlTableSchema FIRST_SCHEMA =
      new MysqlTableSchema(
          1,
          "db1",
          "table1",
          FIRST_POS,
          SERVER_UUID_1 + ":24",
          "CREATE TABLE table1 (id INT)",
          192000,
          TABLE1_COLUMNS,
          Collections.emptyMap());

  private static final BinlogFilePos SECOND_POS =
      new BinlogFilePos(
          "mysql-binlog.001394", 78333, 78345, SERVER_UUID_1 + ":1-488", SERVER_UUID_1);
  private static final MysqlTableSchema SECOND_SCHEMA =
      new MysqlTableSchema(
          2,
          "db1",
          "table2",
          SECOND_POS,
          SERVER_UUID_1 + ":488",
          "CREATE TABLE table2 (id INT, name VARCHAR(255))",
          492000,
          TABLE2_COLUMNS,
          Collections.emptyMap());

  private static final BinlogFilePos THIRD_POS =
      new BinlogFilePos("mysql-binlog.001402", 400, 432, SERVER_UUID_1 + ":1-673", SERVER_UUID_1);
  private static final MysqlTableSchema THIRD_SCHEMA =
      new MysqlTableSchema(
          3,
          "db1",
          "table1",
          THIRD_POS,
          SERVER_UUID_1 + ":673",
          "ALTER TABLE table1 ADD name text, ADD phone VARCHAR(255)",
          892000,
          TABLE1_COLUMNS_NEW,
          Collections.emptyMap());

  private static final BinlogFilePos FOURTH_POS =
      new BinlogFilePos("mysql-binlog.001403", 200, 232, SERVER_UUID_1 + ":1-988", SERVER_UUID_1);
  private static final MysqlTableSchema FOURTH_SCHEMA =
      new MysqlTableSchema(
          4,
          "db1",
          null,
          FOURTH_POS,
          SERVER_UUID_1 + ":988",
          "ALTER TABLE `table2` ADD INDEX (`name`)",
          999988,
          Collections.emptyList(),
          Collections.emptyMap());

  private static final BinlogFilePos FIFTH_POS =
      new BinlogFilePos(
          "mysql-binlog.001403", 890901, 890911, SERVER_UUID_1 + ":1-1024", SERVER_UUID_1);
  private static final MysqlTableSchema FIFTH_SCHEMA =
      new MysqlTableSchema(
          5,
          "db1",
          "table1",
          FIFTH_POS,
          SERVER_UUID_1 + ":1024",
          "DROP TABLE `table1`",
          1010188,
          Collections.emptyList(),
          Collections.emptyMap());

  private static final List<MysqlTableSchema> ALL_TABLE_SCHEMAS =
      ImmutableList.of(FIRST_SCHEMA, SECOND_SCHEMA, THIRD_SCHEMA, FOURTH_SCHEMA, FIFTH_SCHEMA);

  private final MysqlSchemaStore schemaStore =
      spy(
          new MysqlSchemaStore(
              "test",
              "schema_store",
              "schema_archive",
              mock(Jdbi.class),
              mock(MysqlSourceMetrics.class)));

  @Before
  public void setUp() {
    doReturn(ALL_TABLE_SCHEMAS).when(schemaStore).getAllSchemas();
  }

  @Test
  public void testLoadSchemaWithoutGTID() {
    BinlogFilePos pos = new BinlogFilePos("mysql-binlog.001399", 100, 120, null, SERVER_UUID_1);
    schemaStore.loadSchemaCacheUntil(pos);
    assertEquals(2, schemaStore.getSchemaCache().size());
    assertEquals(FIRST_SCHEMA, schemaStore.get("db1", "table1"));
    assertEquals(SECOND_SCHEMA, schemaStore.get("db1", "table2"));

    pos = new BinlogFilePos("mysql-binlog.001403", 400, 420, null, SERVER_UUID_1);
    schemaStore.loadSchemaCacheUntil(pos);
    assertEquals(2, schemaStore.getSchemaCache().size());
    assertEquals(THIRD_SCHEMA, schemaStore.get("db1", "table1"));
    assertEquals(SECOND_SCHEMA, schemaStore.get("db1", "table2"));

    pos = new BinlogFilePos("mysql-binlog.001444", 1100, 1420, null, SERVER_UUID_1);
    schemaStore.loadSchemaCacheUntil(pos);
    assertEquals(1, schemaStore.getSchemaCache().size());
    assertNull(schemaStore.getSchemaCache().get("db1", "table1"));
    assertEquals(SECOND_SCHEMA, schemaStore.get("db1", "table2"));
  }

  @Test
  public void testLoadSchemaWithGTID() {
    schemaStore.getSchemaCache().clear();
    BinlogFilePos pos =
        new BinlogFilePos("mysql-binlog.001398", 300, 310, SERVER_UUID_1 + ":1-589", SERVER_UUID_2);
    schemaStore.loadSchemaCacheUntil(pos);
    assertEquals(2, schemaStore.getSchemaCache().size());
    assertEquals(FIRST_SCHEMA, schemaStore.get("db1", "table1"));
    assertEquals(SECOND_SCHEMA, schemaStore.get("db1", "table2"));

    pos =
        new BinlogFilePos("mysql-binlog.001403", 400, 420, SERVER_UUID_1 + ":1-888", SERVER_UUID_2);
    schemaStore.loadSchemaCacheUntil(pos);
    assertEquals(2, schemaStore.getSchemaCache().size());
    assertEquals(THIRD_SCHEMA, schemaStore.get("db1", "table1"));
    assertEquals(SECOND_SCHEMA, schemaStore.get("db1", "table2"));

    pos =
        new BinlogFilePos(
            "mysql-binlog.001444", 1100, 1420, SERVER_UUID_1 + ":1-1888", SERVER_UUID_2);
    schemaStore.loadSchemaCacheUntil(pos);
    assertEquals(1, schemaStore.getSchemaCache().size());
    assertNull(schemaStore.getSchemaCache().get("db1", "table1"));
    assertEquals(SECOND_SCHEMA, schemaStore.get("db1", "table2"));
  }

  @Test
  public void testCompressSchemaStore() {
    BinlogFilePos currentPos =
        new BinlogFilePos(
            "mysql-binlog.002222", 123, 201, SERVER_UUID_1 + ":1-2888", SERVER_UUID_1);
    schemaStore.loadSchemaCacheUntil(currentPos);

    BinlogFilePos earliestPos =
        new BinlogFilePos(
            "mysql-binlog.001234", 1234, 5677, SERVER_UUID_1 + ":1-10", SERVER_UUID_1);
    assertTrue(schemaStore.getRowIdsToDelete(earliestPos).isEmpty());

    earliestPos =
        new BinlogFilePos("mysql-binlog.001394", 20, 77, SERVER_UUID_1 + ":1-100", SERVER_UUID_1);
    assertEquals(
        new HashSet<>(Collections.singletonList(1L)), schemaStore.getRowIdsToDelete(earliestPos));

    earliestPos =
        new BinlogFilePos("mysql-binlog.001394", 20, 77, SERVER_UUID_1 + ":1-100", SERVER_UUID_2);
    assertEquals(
        new HashSet<>(Collections.singletonList(1L)), schemaStore.getRowIdsToDelete(earliestPos));

    earliestPos =
        new BinlogFilePos("mysql-binlog.001400", 20, 77, SERVER_UUID_1 + ":1-500", SERVER_UUID_2);
    assertEquals(
        new HashSet<>(Collections.singletonList(1L)), schemaStore.getRowIdsToDelete(earliestPos));

    earliestPos =
        new BinlogFilePos("mysql-binlog.001403", 100, 177, SERVER_UUID_1 + ":1-800", SERVER_UUID_1);
    assertEquals(new HashSet<>(Arrays.asList(1L, 3L)), schemaStore.getRowIdsToDelete(earliestPos));

    earliestPos =
        new BinlogFilePos(
            "mysql-binlog.001403", 500, 577, SERVER_UUID_1 + ":1-1000", SERVER_UUID_1);
    assertEquals(
        new HashSet<>(Arrays.asList(1L, 3L, 4L)), schemaStore.getRowIdsToDelete(earliestPos));

    earliestPos =
        new BinlogFilePos(
            "mysql-binlog.002000", 500, 577, SERVER_UUID_1 + ":1-1900", SERVER_UUID_1);
    assertEquals(
        new HashSet<>(Arrays.asList(1L, 3L, 4L, 5L)), schemaStore.getRowIdsToDelete(earliestPos));
  }
}
