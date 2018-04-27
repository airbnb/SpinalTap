/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlDeleteMutation;
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
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} of a {@link DeleteEvent}s to the
 * corresponding list of {@link com.airbnb.spinaltap.mysql.mutation.MysqlMutation}s corresponding to
 * each row change in the event.
 */
final class DeleteMutationMapper extends MysqlMutationMapper<DeleteEvent, MysqlDeleteMutation> {
  DeleteMutationMapper(
      @NonNull final DataSource dataSource,
      @NonNull final TableCache tableCache,
      @NonNull final AtomicReference<Transaction> beginTransaction,
      @NonNull final AtomicReference<Transaction> lastTransaction,
      @NonNull final AtomicLong leaderEpoch) {
    super(dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch);
  }

  @Override
  protected List<MysqlDeleteMutation> mapEvent(
      @NonNull final Table table, @NonNull final DeleteEvent event) {
    final Collection<ColumnMetadata> cols = table.getColumns().values();
    final List<MysqlDeleteMutation> mutations = new ArrayList<>();
    final List<Serializable[]> rows = event.getRows();

    for (int position = 0; position < rows.size(); position++) {
      mutations.add(
          new MysqlDeleteMutation(
              createMetadata(table, event, position),
              new Row(table, zip(rows.get(position), cols))));
    }

    return mutations;
  }
}
