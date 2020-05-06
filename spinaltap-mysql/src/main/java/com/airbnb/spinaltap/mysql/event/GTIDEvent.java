/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import lombok.Getter;

@Getter
public class GTIDEvent extends BinlogEvent {
  private final String gtid;

  public GTIDEvent(long serverId, long timestamp, BinlogFilePos filePos, String gtid) {
    super(0, serverId, timestamp, filePos);
    this.gtid = gtid;
  }
}
