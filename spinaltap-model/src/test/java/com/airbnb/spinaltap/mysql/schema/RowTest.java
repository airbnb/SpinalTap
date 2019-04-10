/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;

import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class RowTest {
  private static final int TABLE_ID = 1;
  private static final String TABLE_NAME = "Users";
  private static final String DB_NAME = "test_db";

  private static final String ID_COLUMN = "id";
  private static final String NAME_COLUMN = "name";

  @Test
  public void testNoPrimaryKey() throws Exception {
    Table table =
        new Table(
            TABLE_ID,
            TABLE_NAME,
            DB_NAME,
            ImmutableList.of(new ColumnMetadata(ID_COLUMN, ColumnDataType.LONGLONG, false, 0)),
            ImmutableList.of());

    Row row =
        new Row(
            table, ImmutableMap.of(ID_COLUMN, new Column(table.getColumns().get(ID_COLUMN), 1)));

    assertNull(row.getPrimaryKeyValue());
  }

  @Test
  public void testNullPrimaryKey() throws Exception {
    Table table =
        new Table(
            TABLE_ID,
            TABLE_NAME,
            DB_NAME,
            ImmutableList.of(new ColumnMetadata(ID_COLUMN, ColumnDataType.LONGLONG, true, 0)),
            ImmutableList.of(ID_COLUMN));

    Row row =
        new Row(
            table, ImmutableMap.of(ID_COLUMN, new Column(table.getColumns().get(ID_COLUMN), null)));

    assertEquals("null", row.getPrimaryKeyValue());
  }

  @Test
  public void testSinglePrimaryKey() throws Exception {
    Table table =
        new Table(
            TABLE_ID,
            TABLE_NAME,
            DB_NAME,
            ImmutableList.of(
                new ColumnMetadata(ID_COLUMN, ColumnDataType.LONGLONG, true, 0),
                new ColumnMetadata(NAME_COLUMN, ColumnDataType.VARCHAR, false, 1)),
            ImmutableList.of(ID_COLUMN));

    Row row =
        new Row(
            table,
            ImmutableMap.of(
                ID_COLUMN, new Column(table.getColumns().get(ID_COLUMN), 1),
                NAME_COLUMN, new Column(table.getColumns().get(NAME_COLUMN), "Bob")));

    assertEquals("1", row.getPrimaryKeyValue());
  }

  @Test
  public void testCompositePrimaryKey() throws Exception {
    Table table =
        new Table(
            TABLE_ID,
            TABLE_NAME,
            DB_NAME,
            ImmutableList.of(
                new ColumnMetadata(ID_COLUMN, ColumnDataType.LONGLONG, true, 0),
                new ColumnMetadata(NAME_COLUMN, ColumnDataType.VARCHAR, true, 1)),
            ImmutableList.of(ID_COLUMN, NAME_COLUMN));

    Row row =
        new Row(
            table,
            ImmutableMap.of(
                ID_COLUMN, new Column(table.getColumns().get(ID_COLUMN), 1),
                NAME_COLUMN, new Column(table.getColumns().get(NAME_COLUMN), "Bob")));

    assertEquals("1Bob", row.getPrimaryKeyValue());
  }
}
