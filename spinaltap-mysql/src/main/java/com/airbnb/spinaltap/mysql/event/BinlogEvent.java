/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.common.source.SourceEvent;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import lombok.Getter;
import lombok.ToString;

/** Represents a Binlog event */
@Getter
@ToString
public abstract class BinlogEvent extends SourceEvent {
  private final long tableId;
  private final long serverId;
  private final BinlogFilePos binlogFilePos;

  public BinlogEvent(long tableId, long serverId, long timestamp, BinlogFilePos binlogFilePos) {
    super(timestamp);

    this.tableId = tableId;
    this.serverId = serverId;
    this.binlogFilePos = binlogFilePos;
  }

  public long getOffset() {
    return (binlogFilePos.getFileNumber() << 32) | binlogFilePos.getPosition();
  }

  public boolean isMutation() {
    return this instanceof WriteEvent || this instanceof DeleteEvent || this instanceof UpdateEvent;
  }
}
