/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
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

public class MysqlUpdateMutationTest {
  @Test
  public void testAsColumnValues() throws Exception {
    Table table =
        new Table(
            1,
            "table_name",
            "db_name",
            null,
            ImmutableList.of(new ColumnMetadata("id", ColumnDataType.LONGLONG, false, 0)),
            ImmutableList.of());

    Row row = new Row(table, ImmutableMap.of("id", new Column(table.getColumns().get("id"), 2)));

    assertEquals(ImmutableMap.of("id", 2), MysqlUpdateMutation.asColumnValues(row));
  }
}
