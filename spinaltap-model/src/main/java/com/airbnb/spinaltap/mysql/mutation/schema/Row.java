/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import com.google.common.collect.ImmutableMap;
import lombok.Value;

@Value
public final class Row {
  private final Table table;
  private final ImmutableMap<String, Column> columns;

  @SuppressWarnings("unchecked")
  public <T> T getValue(String columnName) {
    return (T) columns.get(columnName).getValue();
  }

  public String getPrimaryKeyValue() {
    if (!table.getPrimaryKey().isPresent()) {
      return null;
    }

    StringBuilder value = new StringBuilder();
    table
        .getPrimaryKey()
        .get()
        .getColumns()
        .keySet()
        .forEach(
            key -> {
              value.append(columns.get(key).getValue());
            });

    return value.toString();
  }

  public boolean containsColumn(String columnName) {
    return columns.containsKey(columnName);
  }
}
