/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import lombok.Getter;

@Getter
public class XidEvent extends BinlogEvent {
  private final long xid;

  public XidEvent(long serverId, long timestamp, BinlogFilePos filePos, long xid) {
    super(0l, serverId, timestamp, filePos);

    this.xid = xid;
  }
}
