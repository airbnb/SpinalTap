/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import lombok.Value;

/** Represents additional metadata on a MySQL {@link Column}. */
@Value
public class ColumnMetadata {
  private final String name;
  private final ColumnDataType colType;
  private final boolean isPrimaryKey;
  private final int position;
}
