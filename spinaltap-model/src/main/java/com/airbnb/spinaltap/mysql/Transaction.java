/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import lombok.Value;

/** Represents a MySQL Transaction boundary in the binlog. */
@Value
public class Transaction {
  private final long timestamp;
  private final long offset;
  private final BinlogFilePos position;
}
