/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import java.io.Serializable;
import lombok.Value;

/** Represents a MySQL column. */
@Value
public class Column {
  private final ColumnMetadata metadata;
  private final Serializable value;
}
