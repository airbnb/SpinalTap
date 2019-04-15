/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.validator;

import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import org.junit.Test;

public class MutationSchemaValidatorTest {
  private static final String ID_COLUMN = "id";
  private static final String NAME_COLUMN = "name";
  private static final String AGE_COLUMN = "age";

  private static final Table TABLE =
      new Table(
          0L,
          "Users",
          "test_db",
          ImmutableList.of(
              new ColumnMetadata(ID_COLUMN, ColumnDataType.LONGLONG, true, 0),
              new ColumnMetadata(NAME_COLUMN, ColumnDataType.VARCHAR, false, 1),
              new ColumnMetadata(AGE_COLUMN, ColumnDataType.INT24, false, 2)),
          ImmutableList.of(ID_COLUMN));

  private static final MysqlMutationMetadata MUTATION_METADATA =
      new MysqlMutationMetadata(null, null, TABLE, 0L, 0L, 0L, null, null, 0L, 0);

  private final MutationSchemaValidator validator =
      new MutationSchemaValidator(
          (mutation) -> {
            throw new IllegalStateException();
          });

  @Test
  public void testValidSchema() throws Exception {
    Row row =
        new Row(
            TABLE,
            ImmutableMap.of(
                ID_COLUMN, createColumn(ID_COLUMN, ColumnDataType.LONGLONG, true, 1L, 0),
                NAME_COLUMN, createColumn(NAME_COLUMN, ColumnDataType.VARCHAR, false, "bob", 1),
                AGE_COLUMN, createColumn(AGE_COLUMN, ColumnDataType.INT24, false, 25, 2)));

    validator.validate(createMutation(row));
  }

  @Test(expected = IllegalStateException.class)
  public void testMissingColumn() throws Exception {
    Row row =
        new Row(
            TABLE,
            ImmutableMap.of(
                ID_COLUMN, createColumn(ID_COLUMN, ColumnDataType.LONGLONG, true, 1L, 0),
                NAME_COLUMN, createColumn(NAME_COLUMN, ColumnDataType.VARCHAR, false, "bob", 1)));

    validator.validate(createMutation(row));
  }

  @Test(expected = IllegalStateException.class)
  public void testIncorrectColumn() throws Exception {
    Row row =
        new Row(
            TABLE,
            ImmutableMap.of(
                ID_COLUMN,
                createColumn(ID_COLUMN, ColumnDataType.LONGLONG, true, 1L, 0),
                NAME_COLUMN,
                createColumn(NAME_COLUMN, ColumnDataType.VARCHAR, false, "bob", 1),
                AGE_COLUMN,
                createColumn(AGE_COLUMN, ColumnDataType.INT24, false, 25, 2),
                "bad_column",
                createColumn("bad_column", ColumnDataType.VARCHAR, false, "bad", 3)));

    validator.validate(createMutation(row));
  }

  @Test(expected = IllegalStateException.class)
  public void testIncorrectColumnDataType() throws Exception {
    Row row =
        new Row(
            TABLE,
            ImmutableMap.of(
                ID_COLUMN, createColumn(ID_COLUMN, ColumnDataType.LONGLONG, true, 1L, 0),
                NAME_COLUMN, createColumn(NAME_COLUMN, ColumnDataType.VARCHAR, false, "bob", 1),
                AGE_COLUMN, createColumn(AGE_COLUMN, ColumnDataType.LONGLONG, false, 25, 2)));

    validator.validate(createMutation(row));
  }

  private MysqlMutation createMutation(Row row) {
    return new MysqlInsertMutation(MUTATION_METADATA, row);
  }

  private Column createColumn(
      String name, ColumnDataType dataType, boolean isPk, Serializable value, int position) {
    return new Column(new ColumnMetadata(name, dataType, isPk, position), value);
  }
}
