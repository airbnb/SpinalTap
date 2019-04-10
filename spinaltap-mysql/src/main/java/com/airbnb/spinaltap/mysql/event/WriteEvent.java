/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;

@Getter
public final class WriteEvent extends BinlogEvent {
  private final List<Serializable[]> rows;

  public WriteEvent(
      long tableId,
      long serverId,
      long timestamp,
      BinlogFilePos filePos,
      List<Serializable[]> rows) {
    super(tableId, serverId, timestamp, filePos);

    this.rows = rows;
  }

  @Override
  public int size() {
    return rows.size();
  }
}
