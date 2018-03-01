/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import static org.junit.Assert.assertEquals;

import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class MysqlKeyProviderTest {
  private static final String ID_COLUMN = "id";

  private static final Table TABLE =
      new Table(
          0L,
          "users",
          "test",
          ImmutableList.of(new ColumnMetadata(ID_COLUMN, ColumnDataType.LONGLONG, true, 0)),
          ImmutableList.of(ID_COLUMN));

  private static final MysqlMutationMetadata MUTATION_METADATA =
      new MysqlMutationMetadata(null, null, TABLE, 0L, 0L, 0L, null, null, 0L, 0);

  @Test
  public void testGetKey() throws Exception {
    Row row =
        new Row(
            TABLE, ImmutableMap.of(ID_COLUMN, new Column(TABLE.getColumns().get(ID_COLUMN), 1234)));
    MysqlMutation mutation = new MysqlInsertMutation(MUTATION_METADATA, row);

    assertEquals("test:users:1234", MysqlKeyProvider.INSTANCE.get(mutation));
  }

  @Test
  public void testGetNullKey() throws Exception {
    Row row =
        new Row(
            TABLE, ImmutableMap.of(ID_COLUMN, new Column(TABLE.getColumns().get(ID_COLUMN), null)));
    MysqlMutation mutation = new MysqlInsertMutation(MUTATION_METADATA, row);

    assertEquals("test:users:null", MysqlKeyProvider.INSTANCE.get(mutation));
  }
}
