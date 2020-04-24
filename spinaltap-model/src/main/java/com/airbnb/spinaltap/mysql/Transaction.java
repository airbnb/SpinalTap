/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import lombok.RequiredArgsConstructor;
import lombok.Value;

/** Represents a MySQL Transaction boundary in the binlog. */
@Value
@RequiredArgsConstructor
public class Transaction {
  private final long timestamp;
  private final long offset;
  private final BinlogFilePos position;
  private final String gtid;

  public Transaction(long timestamp, long offset, BinlogFilePos position) {
    this.timestamp = timestamp;
    this.offset = offset;
    this.position = position;
    this.gtid = null;
  }
}
