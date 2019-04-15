/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} of a {@link WriteEvent} to a list of
 * {@link com.airbnb.spinaltap.mysql.mutation.MysqlMutation}s corresponding to each row change in
 * the event.
 */
class InsertMutationMapper extends MysqlMutationMapper<WriteEvent, MysqlInsertMutation> {
  InsertMutationMapper(
      @NonNull final DataSource dataSource,
      @NonNull final TableCache tableCache,
      @NonNull final AtomicReference<Transaction> beginTransaction,
      @NonNull final AtomicReference<Transaction> lastTransaction,
      @NonNull final AtomicLong leaderEpoch) {
    super(dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch);
  }

  @Override
  protected List<MysqlInsertMutation> mapEvent(
      @NonNull final Table table, @NonNull final WriteEvent event) {
    final List<Serializable[]> rows = event.getRows();
    final List<MysqlInsertMutation> mutations = new ArrayList<>();
    final Collection<ColumnMetadata> cols = table.getColumns().values();

    for (int position = 0; position < rows.size(); position++) {
      mutations.add(
          new MysqlInsertMutation(
              createMetadata(table, event, position),
              new Row(table, zip(rows.get(position), cols))));
    }

    return mutations;
  }
}
