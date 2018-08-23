/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class PrimaryKey {
  /** Note: Insertion order should be preserved in the choice of {@link java.util.Map} implement. */
  private final ImmutableMap<String, ColumnMetadata> columns;
}
