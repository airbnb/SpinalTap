/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlDeleteMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.MysqlUpdateMutation;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} of a {@link UpdateEvent}s to the
 * corresponding list of {@link com.airbnb.spinaltap.mysql.mutation.MysqlMutation}s corresponding to
 * each row change in the event.
 */
final class UpdateMutationMapper extends MysqlMutationMapper<UpdateEvent, MysqlMutation> {
  UpdateMutationMapper(
      @NonNull final DataSource dataSource,
      @NonNull final TableCache tableCache,
      @NonNull final AtomicReference<Transaction> beginTransaction,
      @NonNull final AtomicReference<Transaction> lastTransaction,
      @NonNull final AtomicLong leaderEpoch) {
    super(dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch);
  }

  @Override
  protected List<MysqlMutation> mapEvent(
      @NonNull final Table table, @NonNull final UpdateEvent event) {
    final List<MysqlMutation> mutations = Lists.newArrayList();
    final Collection<ColumnMetadata> cols = table.getColumns().values();
    final List<Map.Entry<Serializable[], Serializable[]>> rows = event.getRows();

    for (int position = 0; position < rows.size(); position++) {
      MysqlMutationMetadata metadata = createMetadata(table, event, position);

      final Row previousRow = new Row(table, zip(rows.get(position).getKey(), cols));
      final Row newRow = new Row(table, zip(rows.get(position).getValue(), cols));

      // If PK value has changed, then delete before image and insert new image
      // to retain invariant that a mutation captures changes to a single PK
      if (table.getPrimaryKey().isPresent()
          && !previousRow.getPrimaryKeyValue().equals(newRow.getPrimaryKeyValue())) {
        mutations.add(new MysqlDeleteMutation(metadata, previousRow));
        mutations.add(new MysqlInsertMutation(metadata, newRow));
      } else {
        mutations.add(new MysqlUpdateMutation(metadata, previousRow, newRow));
      }
    }

    return mutations;
  }
}
