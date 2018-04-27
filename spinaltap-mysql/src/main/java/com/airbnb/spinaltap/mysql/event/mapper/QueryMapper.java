/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
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
  private static final Pattern TABLE_DDL_SQL_PATTERN =
      Pattern.compile("^(ALTER|CREATE|DROP|RENAME)\\s+TABLE", Pattern.CASE_INSENSITIVE);
  private static final Pattern INDEX_DDL_SQL_PATTERN =
      Pattern.compile(
          "^((CREATE(\\s+(UNIQUE|FULLTEXT|SPATIAL))?)|DROP)\\s+INDEX", Pattern.CASE_INSENSITIVE);
  private static final Pattern DATABASE_DDL_SQL_PATTERN =
      Pattern.compile("^(CREATE|DROP)\\s+(DATABASE|SCHEMA)", Pattern.CASE_INSENSITIVE);

  private final AtomicReference<Transaction> beginTransaction;
  private final SchemaTracker schemaTracker;

  public List<MysqlMutation> map(@NonNull final QueryEvent event) {
    if (isTransactionBegin(event)) {
      beginTransaction.set(
          new Transaction(event.getTimestamp(), event.getOffset(), event.getBinlogFilePos()));
    }

    if (isDDLStatement(event)) {
      schemaTracker.processDDLStatement(event);
    }

    return Collections.emptyList();
  }

  private boolean isTransactionBegin(final QueryEvent event) {
    return event.getSql().equals(BEGIN_STATEMENT);
  }

  private boolean isDDLStatement(final QueryEvent event) {
    final String sql = event.getSql();
    return TABLE_DDL_SQL_PATTERN.matcher(sql).find()
        || INDEX_DDL_SQL_PATTERN.matcher(sql).find()
        || DATABASE_DDL_SQL_PATTERN.matcher(sql).find();
  }
}
