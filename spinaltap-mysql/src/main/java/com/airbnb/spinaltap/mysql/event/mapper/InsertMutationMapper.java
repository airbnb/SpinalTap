/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
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

class InsertMutationMapper extends MysqlMutationMapper<WriteEvent, MysqlInsertMutation> {
  InsertMutationMapper(
      DataSource dataSource,
      TableCache tableCache,
      AtomicReference<Transaction> beginTransaction,
      AtomicReference<Transaction> lastTransaction,
      AtomicLong leaderEpoch) {
    super(dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch);
  }

  @Override
  protected List<MysqlInsertMutation> mapEvent(Table table, WriteEvent event) {
    List<Serializable[]> rows = event.getRows();
    List<MysqlInsertMutation> mutations = new ArrayList<>();
    Collection<ColumnMetadata> cols = table.getColumns().values();

    for (int position = 0; position < rows.size(); position++) {
      mutations.add(
          new MysqlInsertMutation(
              createMetadata(table, event, position),
              new Row(table, zip(rows.get(position), cols))));
    }

    return mutations;
  }
}
