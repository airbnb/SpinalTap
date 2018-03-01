/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class XidMapper implements Mapper<XidEvent, List<MysqlMutation>> {
  private final AtomicReference<Transaction> endTransaction;
  private final MysqlSourceMetrics metrics;

  public List<MysqlMutation> map(XidEvent event) {
    endTransaction.set(
        new Transaction(event.getTimestamp(), event.getOffset(), event.getBinlogFilePos()));

    metrics.transactionReceived();
    return Collections.emptyList();
  }
}
