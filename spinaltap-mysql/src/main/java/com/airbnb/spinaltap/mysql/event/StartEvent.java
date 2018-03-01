/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event;

import com.airbnb.spinaltap.mysql.BinlogFilePos;

public class StartEvent extends BinlogEvent {
  public StartEvent(long serverId, long timestamp, BinlogFilePos filePos) {
    super(0L, serverId, timestamp, filePos);
  }
}
