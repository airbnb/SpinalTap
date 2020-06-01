/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManager;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that keeps track of {@link
 * QueryEvent}s. This is used to detect schema changes from DDL statements, and mark BEGIN
 * statements.
 */
@Slf4j
@RequiredArgsConstructor
final class QueryMapper implements Mapper<QueryEvent, List<MysqlMutation>> {
  private static final String BEGIN_STATEMENT = "BEGIN";

  private final AtomicReference<Transaction> beginTransaction;
  private final AtomicReference<Transaction> lastTransaction;
  private final AtomicReference<String> gtid;
  private final MysqlSchemaManager schemaManager;

  public List<MysqlMutation> map(@NonNull final QueryEvent event) {
    Transaction transaction =
        new Transaction(
            event.getTimestamp(), event.getOffset(), event.getBinlogFilePos(), gtid.get());
    if (isTransactionBegin(event)) {
      beginTransaction.set(transaction);
    } else {
      // DDL is also a transaction
      lastTransaction.set(transaction);
      schemaManager.processDDL(event, gtid.get());
    }

    return Collections.emptyList();
  }

  private boolean isTransactionBegin(final QueryEvent event) {
    return event.getSql().equals(BEGIN_STATEMENT);
  }
}
