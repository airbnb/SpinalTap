/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class UpdateEvent extends BinlogEvent {
  private final List<Map.Entry<Serializable[], Serializable[]>> rows;

  public UpdateEvent(
      long tableId,
      long serverId,
      long timestamp,
      BinlogFilePos filePos,
      List<Map.Entry<Serializable[], Serializable[]>> rows) {
    super(tableId, serverId, timestamp, filePos);

    this.rows = rows;
  }

  @Override
  public int size() {
    return rows.size();
  }
}
