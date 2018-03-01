/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import lombok.Getter;

@Getter
public class QueryEvent extends BinlogEvent {
  private final String database;
  private final String sql;

  public QueryEvent(
      long serverId, long timestamp, BinlogFilePos filePos, String database, String sql) {
    super(0l, serverId, timestamp, filePos);

    this.database = database;
    this.sql = sql;
  }
}
