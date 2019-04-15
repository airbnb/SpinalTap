/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.airbnb.spinaltap.mysql.schema.ColumnInfo;
import com.airbnb.spinaltap.mysql.schema.LatestMysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaUtil;
import com.airbnb.spinaltap.mysql.schema.MysqlTableSchema;
import com.airbnb.spinaltap.mysql.schema.SchemaStore;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TableCacheTest {
  private static final String DATABASE_NAME = "db";
  private static final String TABLE_NAME = "test";
  private static final long TABLE_ID = 1l;

  private static final Table TABLE =
      new Table(
          TABLE_ID,
          TABLE_NAME,
          DATABASE_NAME,
          Arrays.asList(
              new ColumnMetadata("col1", ColumnDataType.TINY, true, 0),
              new ColumnMetadata("col2", ColumnDataType.STRING, false, 1),
              new ColumnMetadata("col3", ColumnDataType.FLOAT, true, 2),
              new ColumnMetadata("col4", ColumnDataType.LONG, false, 3)),
          Arrays.asList("col1", "col3"));

  private static final MysqlTableSchema TABLE_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          "host",
          "database",
          "table",
          "",
          Arrays.asList(
              new ColumnInfo("table", "col1", "TINY", true),
              new ColumnInfo("table", "col2", "STRING", false),
              new ColumnInfo("table", "col3", "FLOAT", true),
              new ColumnInfo("table", "col4", "LONG", false)));

  private static final Table TABLE_UPDATED =
      new Table(
          TABLE_ID,
          TABLE_NAME,
          DATABASE_NAME,
          Arrays.asList(
              new ColumnMetadata("col1", ColumnDataType.TINY, true, 0),
              new ColumnMetadata("col2", ColumnDataType.STRING, false, 1),
              new ColumnMetadata("col3", ColumnDataType.FLOAT, true, 2)),
          Arrays.asList("col1", "col3"));

  private static final MysqlTableSchema TABLE_SCHEMA_UPDATED =
      MysqlSchemaUtil.createTableSchema(
          "host",
          "database",
          "table",
          "",
          Arrays.asList(
              new ColumnInfo("table", "col1", "TINY", true),
              new ColumnInfo("table", "col2", "STRING", false),
              new ColumnInfo("table", "col3", "FLOAT", true)));

  private static final MysqlTableSchema TABLE_SCHEMA_LARGE_STUB =
      MysqlSchemaUtil.createTableSchema(
          "host",
          "database",
          "table",
          "",
          Arrays.asList(
              new ColumnInfo("table", "col1", "TINY", true),
              new ColumnInfo("table", "col2", "STRING", false),
              new ColumnInfo("table", "col3", "FLOAT", true),
              new ColumnInfo("table", "col4", "LONG", false),
              new ColumnInfo("table", "col5", "VARCHAR", false)));

  private final SchemaStore<MysqlTableSchema> schemaReader = mock(LatestMysqlSchemaStore.class);
  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);
  private final BinlogFilePos binlogFilePos = new BinlogFilePos("mysql-bin-changelog.000532");

  @Test
  public void test() throws Exception {
    TableCache tableCache = new TableCache(schemaReader);

    List<ColumnDataType> columnTypes =
        Arrays.asList(
            ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT, ColumnDataType.LONG);

    when(schemaReader.query(DATABASE_NAME, TABLE_NAME, binlogFilePos)).thenReturn(TABLE_SCHEMA);

    assertNull(tableCache.get(TABLE_ID));

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, binlogFilePos, columnTypes);

    Table table = tableCache.get(TABLE_ID);
    assertEquals(TABLE, table);
    verify(schemaReader, times(1)).query(DATABASE_NAME, TABLE_NAME, binlogFilePos);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, binlogFilePos, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE, table);
    verify(schemaReader, times(1)).query(DATABASE_NAME, TABLE_NAME, binlogFilePos);

    columnTypes = Arrays.asList(ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT);

    when(schemaReader.query(DATABASE_NAME, TABLE_NAME, binlogFilePos))
        .thenReturn(TABLE_SCHEMA_UPDATED);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, binlogFilePos, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE_UPDATED, table);
    verify(schemaReader, times(2)).query(DATABASE_NAME, TABLE_NAME, binlogFilePos);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, binlogFilePos, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE_UPDATED, table);
    verify(schemaReader, times(2)).query(DATABASE_NAME, TABLE_NAME, binlogFilePos);

    // Schema reader now returns schema with 5 columns, but columnTypes has size 4
    columnTypes =
        Arrays.asList(
            ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT, ColumnDataType.LONG);

    when(schemaReader.query(DATABASE_NAME, TABLE_NAME, binlogFilePos))
        .thenReturn(TABLE_SCHEMA_LARGE_STUB);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, binlogFilePos, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE, table);
    verify(schemaReader, times(3)).query(DATABASE_NAME, TABLE_NAME, binlogFilePos);
  }

  @Test
  public void testNewTableName() throws Exception {
    TableCache tableCache = new TableCache(schemaReader);
    String newTable = "new_table";

    when(schemaReader.query(DATABASE_NAME, TABLE_NAME, binlogFilePos)).thenReturn(TABLE_SCHEMA);
    List<ColumnDataType> columnTypes =
        Arrays.asList(
            ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT, ColumnDataType.LONG);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, binlogFilePos, columnTypes);

    verify(schemaReader, times(1)).query(DATABASE_NAME, TABLE_NAME, binlogFilePos);

    when(schemaReader.query(DATABASE_NAME, newTable, binlogFilePos))
        .thenReturn(TABLE_SCHEMA_UPDATED);
    columnTypes = Arrays.asList(ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT);

    tableCache.addOrUpdate(TABLE_ID, newTable, DATABASE_NAME, binlogFilePos, columnTypes);

    verify(schemaReader, times(1)).query(DATABASE_NAME, newTable, binlogFilePos);
    verifyZeroInteractions(metrics);
  }
}
