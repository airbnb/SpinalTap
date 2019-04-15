/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Base {@link com.airbnb.spinaltap.common.util.Mapper} implement that maps a {@link BinlogEvent}s
 * to its corresponding {@link com.airbnb.spinaltap.mysql.mutation.MysqlMutation}s.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class MysqlMutationMapper<R extends BinlogEvent, T extends MysqlMutation>
    implements Mapper<R, List<T>> {
  @NonNull private final DataSource dataSource;
  @NonNull private final TableCache tableCache;
  @NonNull private final AtomicReference<Transaction> beginTransaction;
  @NonNull private final AtomicReference<Transaction> lastTransaction;
  @NonNull private final AtomicLong leaderEpoch;

  public static Mapper<BinlogEvent, List<? extends Mutation<?>>> create(
      @NonNull final DataSource dataSource,
      @NonNull final TableCache tableCache,
      @NonNull final SchemaTracker schemaTracker,
      @NonNull final AtomicLong leaderEpoch,
      @NonNull final AtomicReference<Transaction> beginTransaction,
      @NonNull final AtomicReference<Transaction> lastTransaction,
      @NonNull final MysqlSourceMetrics metrics) {
    return ClassBasedMapper.<BinlogEvent, List<? extends Mutation<?>>>builder()
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

  protected abstract List<T> mapEvent(@NonNull final Table table, @NonNull final R event);

  public List<T> map(@NonNull final R event) {
    Table table = tableCache.get(event.getTableId());

    return mapEvent(table, event);
  }

  MysqlMutationMetadata createMetadata(
      @NonNull final Table table, @NonNull final BinlogEvent event, final int eventPosition) {
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

  static ImmutableMap<String, Column> zip(
      @NonNull final Serializable[] row, @NonNull final Collection<ColumnMetadata> columns) {
    if (row.length != columns.size()) {
      log.error("Row length {} and column length {} don't match", row.length, columns.size());
    }

    final ImmutableMap.Builder<String, Column> builder = ImmutableMap.builder();
    final Iterator<ColumnMetadata> columnIterator = columns.iterator();

    for (int position = 0; position < row.length && columnIterator.hasNext(); position++) {
      final ColumnMetadata col = columnIterator.next();
      builder.put(col.getName(), new Column(col, row[position]));
    }

    return builder.build();
  }
}
