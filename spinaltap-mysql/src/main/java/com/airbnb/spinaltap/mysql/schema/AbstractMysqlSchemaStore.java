/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Getter
public abstract class AbstractMysqlSchemaStore {
  @NonNull protected final String source;
  @NonNull protected final MysqlSourceMetrics metrics;

  public void put(
      @NotNull final String database,
      @NotNull final String table,
      @NotNull final BinlogFilePos binlogFilePos,
      final long timestamp,
      @NotNull final String sql,
      @NotNull final List<ColumnInfo> columnInfoList) {
    log.info(
        "Saving new table schema for {}:{}. BinlogFilePos: {}", database, table, binlogFilePos);
    put(
        new MysqlTableSchema(
            exists(database, table) ? getLatestVersion(database, table) + 1 : 0,
            source,
            database,
            table,
            binlogFilePos,
            sql,
            timestamp,
            columnInfoList,
            null));
  }

  public abstract void put(MysqlTableSchema schema);

  public abstract int getLatestVersion(String database, String table);

  public abstract boolean exists(String database, String table);
}
