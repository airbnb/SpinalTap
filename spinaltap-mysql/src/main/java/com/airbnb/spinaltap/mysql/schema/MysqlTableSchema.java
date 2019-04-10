/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.sql.Blob;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = MysqlTableSchema.MysqlTableSchemaBuilder.class)
public final class MysqlTableSchema {
  private final int version;
  private final String source;
  private final String database;
  private final String table;
  private final BinlogFilePos binlogFilePos;
  private final String sql;
  private final long timestamp;
  private final List<ColumnInfo> columnInfo;
  private final Blob metadata;

  @JsonPOJOBuilder(withPrefix = "")
  static final class MysqlTableSchemaBuilder {}
}
