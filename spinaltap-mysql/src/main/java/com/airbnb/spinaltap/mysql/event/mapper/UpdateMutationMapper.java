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

class UpdateMutationMapper extends MysqlMutationMapper<UpdateEvent, MysqlMutation> {
  UpdateMutationMapper(
      DataSource dataSource,
      TableCache tableCache,
      AtomicReference<Transaction> beginTransaction,
      AtomicReference<Transaction> lastTransaction,
      AtomicLong leaderEpoch) {
    super(dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch);
  }

  @Override
  protected List<MysqlMutation> mapEvent(Table table, UpdateEvent event) {
    List<MysqlMutation> mutations = Lists.newArrayList();
    Collection<ColumnMetadata> cols = table.getColumns().values();
    List<Map.Entry<Serializable[], Serializable[]>> rows = event.getRows();
    for (int position = 0; position < rows.size(); position++) {
      MysqlMutationMetadata metadata = createMetadata(table, event, position);

      Row previousRow = new Row(table, zip(rows.get(position).getKey(), cols));
      Row newRow = new Row(table, zip(rows.get(position).getValue(), cols));

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
    ;

    return mutations;
  }
}
