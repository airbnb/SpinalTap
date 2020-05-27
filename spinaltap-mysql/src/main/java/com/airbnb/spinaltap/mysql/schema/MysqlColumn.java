/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
@RequiredArgsConstructor
@JsonDeserialize(builder = MysqlColumn.MysqlColumnBuilder.class)
public class MysqlColumn {
  String name;
  String dataType;
  String columnType;
  boolean primaryKey;

  @JsonPOJOBuilder(withPrefix = "")
  static class MysqlColumnBuilder {}
}
