/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@JsonDeserialize(builder = ColumnInfo.ColumnInfoBuilder.class)
@Builder
@Value
@AllArgsConstructor
public class ColumnInfo {
  private final String table;
  private final String name;
  private final String type;
  private final boolean primaryKey;

  @JsonPOJOBuilder(withPrefix = "")
  static final class ColumnInfoBuilder {}
}
