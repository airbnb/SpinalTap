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
import com.airbnb.spinaltap.mysql.schema.MysqlColumn;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManager;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TableCacheTest {
  private static final String DATABASE_NAME = "db";
  private static final String OVERRIDING_DATABASE_NAME = "overriding_db";
  private static final String TABLE_NAME = "test";
  private static final long TABLE_ID = 1L;

  private static final Table TABLE =
      new Table(
          TABLE_ID,
          TABLE_NAME,
          DATABASE_NAME,
          OVERRIDING_DATABASE_NAME,
          Arrays.asList(
              new ColumnMetadata("col1", ColumnDataType.TINY, true, 0, "TINY"),
              new ColumnMetadata("col2", ColumnDataType.STRING, false, 1, "TEXT"),
              new ColumnMetadata("col3", ColumnDataType.FLOAT, true, 2, "FLOAT"),
              new ColumnMetadata("col4", ColumnDataType.LONG, false, 3, "LONG")),
          Arrays.asList("col1", "col3"));

  private static final List<MysqlColumn> TABLE_COLUMNS =
      Arrays.asList(
          new MysqlColumn("col1", "TINY", "TINY", true),
          new MysqlColumn("col2", "STRING", "TEXT", false),
          new MysqlColumn("col3", "FLOAT", "FLOAT", true),
          new MysqlColumn("col4", "LONG", "LONG", false));

  private static final Table TABLE_UPDATED =
      new Table(
          TABLE_ID,
          TABLE_NAME,
          DATABASE_NAME,
          OVERRIDING_DATABASE_NAME,
          Arrays.asList(
              new ColumnMetadata("col1", ColumnDataType.TINY, true, 0, "TINY"),
              new ColumnMetadata("col2", ColumnDataType.STRING, false, 1, "STRING"),
              new ColumnMetadata("col3", ColumnDataType.FLOAT, true, 2, "FLOAT")),
          Arrays.asList("col1", "col3"));

  private static final List<MysqlColumn> TABLE_COLUMNS_UPDATED =
      Arrays.asList(
          new MysqlColumn("col1", "TINY", "TINY", true),
          new MysqlColumn("col2", "STRING", "STRING", false),
          new MysqlColumn("col3", "FLOAT", "FLOAT", true));

  private static final List<MysqlColumn> TABLE_COLUMNS_LARGE_STUB =
      Arrays.asList(
          new MysqlColumn("col1", "TINY", "TINY", true),
          new MysqlColumn("col2", "STRING", "TEXT", false),
          new MysqlColumn("col3", "FLOAT", "FLOAT", true),
          new MysqlColumn("col4", "LONG", "LONG", false),
          new MysqlColumn("col5", "VARCHAR", "VARCHAR", false));

  private final MysqlSchemaManager schemaManager = mock(MysqlSchemaManager.class);
  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);
  private final BinlogFilePos binlogFilePos = new BinlogFilePos("mysql-bin-changelog.000532");

  @Test
  public void test() throws Exception {
    TableCache tableCache = new TableCache(schemaManager, OVERRIDING_DATABASE_NAME);

    List<ColumnDataType> columnTypes =
        Arrays.asList(
            ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT, ColumnDataType.LONG);

    when(schemaManager.getTableColumns(DATABASE_NAME, TABLE_NAME)).thenReturn(TABLE_COLUMNS);

    assertNull(tableCache.get(TABLE_ID));

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, columnTypes);

    Table table = tableCache.get(TABLE_ID);
    assertEquals(TABLE, table);
    verify(schemaManager, times(1)).getTableColumns(DATABASE_NAME, TABLE_NAME);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE, table);
    verify(schemaManager, times(1)).getTableColumns(DATABASE_NAME, TABLE_NAME);

    columnTypes = Arrays.asList(ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT);

    when(schemaManager.getTableColumns(DATABASE_NAME, TABLE_NAME))
        .thenReturn(TABLE_COLUMNS_UPDATED);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE_UPDATED, table);
    verify(schemaManager, times(2)).getTableColumns(DATABASE_NAME, TABLE_NAME);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE_UPDATED, table);
    verify(schemaManager, times(2)).getTableColumns(DATABASE_NAME, TABLE_NAME);

    // Schema reader now returns schema with 5 columns, but columnTypes has size 4
    columnTypes =
        Arrays.asList(
            ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT, ColumnDataType.LONG);

    when(schemaManager.getTableColumns(DATABASE_NAME, TABLE_NAME))
        .thenReturn(TABLE_COLUMNS_LARGE_STUB);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, columnTypes);

    table = tableCache.get(TABLE_ID);
    assertEquals(TABLE, table);
    verify(schemaManager, times(3)).getTableColumns(DATABASE_NAME, TABLE_NAME);
  }

  @Test
  public void testNewTableName() throws Exception {
    TableCache tableCache = new TableCache(schemaManager, OVERRIDING_DATABASE_NAME);
    String newTable = "new_table";

    when(schemaManager.getTableColumns(DATABASE_NAME, TABLE_NAME)).thenReturn(TABLE_COLUMNS);
    List<ColumnDataType> columnTypes =
        Arrays.asList(
            ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT, ColumnDataType.LONG);

    tableCache.addOrUpdate(TABLE_ID, TABLE_NAME, DATABASE_NAME, columnTypes);

    verify(schemaManager, times(1)).getTableColumns(DATABASE_NAME, TABLE_NAME);

    when(schemaManager.getTableColumns(DATABASE_NAME, newTable)).thenReturn(TABLE_COLUMNS_UPDATED);
    columnTypes = Arrays.asList(ColumnDataType.TINY, ColumnDataType.STRING, ColumnDataType.FLOAT);

    tableCache.addOrUpdate(TABLE_ID, newTable, DATABASE_NAME, columnTypes);

    verify(schemaManager, times(1)).getTableColumns(DATABASE_NAME, newTable);
    verifyZeroInteractions(metrics);
  }
}
