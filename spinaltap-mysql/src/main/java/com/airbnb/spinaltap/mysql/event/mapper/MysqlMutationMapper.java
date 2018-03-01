/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.ClassBasedMapper;
import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Maps a BinlogEvent to a Mutation */
@Slf4j
@RequiredArgsConstructor
public abstract class MysqlMutationMapper<R extends BinlogEvent, T extends MysqlMutation>
    implements Mapper<R, List<T>> {
  private final DataSource dataSource;
  private final TableCache tableCache;
  private final AtomicReference<Transaction> beginTransaction;
  private final AtomicReference<Transaction> lastTransaction;
  private final AtomicLong leaderEpoch;

  public static Mapper<BinlogEvent, List<? extends Mutation<?>>> create(
      DataSource dataSource,
      TableCache tableCache,
      SchemaTracker schemaTracker,
      AtomicLong leaderEpoch,
      AtomicReference<Transaction> beginTransaction,
      AtomicReference<Transaction> lastTransaction,
      MysqlSourceMetrics metrics) {
    return new ClassBasedMapper.Builder<BinlogEvent, List<? extends Mutation<?>>>()
        .addMapper(TableMapEvent.class, new TableMapMapper(tableCache))
        .addMapper(QueryEvent.class, new QueryMapper(beginTransaction, schemaTracker))
        .addMapper(XidEvent.class, new XidMapper(lastTransaction, metrics))
        .addMapper(StartEvent.class, new StartMapper(dataSource, tableCache, metrics))
        .addMapper(
            UpdateEvent.class,
            new UpdateMutationMapper(
                dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch))
        .addMapper(
            WriteEvent.class,
            new InsertMutationMapper(
                dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch))
        .addMapper(
            DeleteEvent.class,
            new DeleteMutationMapper(
                dataSource, tableCache, beginTransaction, lastTransaction, leaderEpoch))
        .build();
  }

  protected abstract List<T> mapEvent(Table table, R event);

  public List<T> map(R event) {
    Table table = tableCache.get(event.getTableId());

    return mapEvent(table, event);
  }

  protected MysqlMutationMetadata createMetadata(
      final Table table, final BinlogEvent event, final int eventPosition) {
    return new MysqlMutationMetadata(
        dataSource,
        event.getBinlogFilePos(),
        table,
        event.getServerId(),
        event.getOffset(),
        event.getTimestamp(),
        beginTransaction.get(),
        lastTransaction.get(),
        leaderEpoch.get(),
        eventPosition);
  }

  protected static ImmutableMap<String, Column> zip(
      Serializable[] row, Collection<ColumnMetadata> columns) {
    if (row.length != columns.size()) {
      log.error("Row length {} and column length {} don't match", row.length, columns.size());
    }

    ImmutableMap.Builder<String, Column> builder = ImmutableMap.builder();
    Iterator<ColumnMetadata> columnIterator = columns.iterator();

    for (int position = 0; position < row.length && columnIterator.hasNext(); position++) {
      ColumnMetadata col = columnIterator.next();
      builder.put(col.getName(), new Column(col, row[position]));
    }

    return builder.build();
  }
}
