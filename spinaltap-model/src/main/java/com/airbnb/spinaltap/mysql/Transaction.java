/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import lombok.Value;

@Value
public final class Transaction {
  private final long timestamp;
  private final long offset;
  private final BinlogFilePos position;
}
