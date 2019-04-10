/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public final class TableMapEvent extends BinlogEvent {
  private final String database;
  private final String table;
  private final List<ColumnDataType> columnTypes;

  public TableMapEvent(
      long tableId,
      long serverId,
      long timestamp,
      BinlogFilePos filePos,
      String database,
      String table,
      byte[] columnTypeCodes) {
    super(tableId, serverId, timestamp, filePos);

    this.database = database;
    this.table = table;
    this.columnTypes = new ArrayList<>();

    for (byte code : columnTypeCodes) {
      columnTypes.add(ColumnDataType.byCode(code));
    }
  }
}
