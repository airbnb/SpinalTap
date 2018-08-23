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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that keeps track of {@link
 * Transaction} end information from {@link XidEvent}s, which will be appended as metadata to
 * streamed {@link MysqlMutation}s.
 */
@Slf4j
@RequiredArgsConstructor
final class XidMapper implements Mapper<XidEvent, List<MysqlMutation>> {
  @NonNull private final AtomicReference<Transaction> endTransaction;
  @NonNull private final MysqlSourceMetrics metrics;

  public List<MysqlMutation> map(@NonNull final XidEvent event) {
    endTransaction.set(
        new Transaction(event.getTimestamp(), event.getOffset(), event.getBinlogFilePos()));

    metrics.transactionReceived();
    return Collections.emptyList();
  }
}
