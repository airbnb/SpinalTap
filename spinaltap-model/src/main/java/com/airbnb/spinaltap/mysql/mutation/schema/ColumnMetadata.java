/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/** Represents additional metadata on a MySQL {@link Column}. */
@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class ColumnMetadata {
  private final String name;
  private final ColumnDataType colType;
  private final boolean isPrimaryKey;
  private final int position;
  private String rawColumnType;
}
