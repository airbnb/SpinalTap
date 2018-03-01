/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import org.junit.Test;
import org.skife.jdbi.v2.StatementContext;

public class ColumnMapperTest {
  @Test
  public void testColumnMap() throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    StatementContext context = mock(StatementContext.class);

    when(resultSet.getString("COLUMN_NAME")).thenReturn("col1");
    when(resultSet.getString("COLUMN_KEY")).thenReturn("PRI");

    ColumnInfo column = MysqlSchemaUtil.COLUMN_MAPPER.map(0, resultSet, context);
    assertEquals("col1", column.getName());
    assertTrue(column.isPrimaryKey());

    when(resultSet.getString("COLUMN_NAME")).thenReturn("col2");
    when(resultSet.getString("COLUMN_KEY")).thenReturn("");

    column = MysqlSchemaUtil.COLUMN_MAPPER.map(0, resultSet, context);
    assertEquals("col2", column.getName());
    assertFalse(column.isPrimaryKey());
  }
}
