/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import com.google.common.collect.ImmutableMap;
import lombok.Value;

/** Represents a MySQL row. */
@Value
public final class Row {
  private final Table table;
  private final ImmutableMap<String, Column> columns;

  @SuppressWarnings("unchecked")
  public <T> T getValue(final String columnName) {
    return (T) columns.get(columnName).getValue();
  }

  public String getPrimaryKeyValue() {
    if (!table.getPrimaryKey().isPresent()) {
      return null;
    }

    final StringBuilder value = new StringBuilder();
    table
        .getPrimaryKey()
        .get()
        .getColumns()
        .keySet()
        .stream()
        .map(columns::get)
        .map(Column::getValue)
        .forEach(value::append);

    return value.toString();
  }

  public boolean containsColumn(final String columnName) {
    return columns.containsKey(columnName);
  }
}
