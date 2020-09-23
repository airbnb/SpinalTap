/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.airbnb.spinaltap.mysql.schema.MysqlColumn;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.Min;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents an in-memory cache for storing table schema and metadata used during the
 * transformation of MySQL binlog events to {@link com.airbnb.spinaltap.Mutation}s.
 */
@Slf4j
@RequiredArgsConstructor
public class TableCache {
  private final MysqlSchemaManager schemaManager;
  private final String overridingDatabase;
  private final Cache<Long, Table> tableCache = CacheBuilder.newBuilder().maximumSize(200).build();

  /**
   * @return the {@link Table} cache entry for the given table id if present, otherwise {@code null}
   */
  public Table get(@Min(0) final long tableId) {
    return tableCache.getIfPresent(tableId);
  }

  /**
   * @return {@code True} if a cache entry exists for the given table id, otherwise {@code False}.
   */
  public boolean contains(@Min(0) final long tableId) {
    return tableCache.getIfPresent(tableId) != null;
  }

  /**
   * Adds or replaces (if already exists) a {@link Table} entry in the cache for the given table id.
   *
   * @param tableId The table id
   * @param tableName The table name
   * @param database The database name
   * @param columnTypes The list of columnd data types
   */
  public void addOrUpdate(
      @Min(0) final long tableId,
      @NonNull final String tableName,
      @NonNull final String database,
      @NonNull final List<ColumnDataType> columnTypes)
      throws Exception {
    final Table table = tableCache.getIfPresent(tableId);

    if (table == null || !validTable(table, tableName, database, columnTypes)) {
      tableCache.put(tableId, fetchTable(tableId, database, tableName, columnTypes));
    }
  }

  /** Clears the cache by invalidating all entries. */
  public void clear() {
    tableCache.invalidateAll();
  }

  /** Checks whether the table representation is valid */
  private boolean validTable(
      final Table table,
      final String tableName,
      final String databaseName,
      final List<ColumnDataType> columnTypes) {
    return table.getName().equals(tableName)
        && table.getDatabase().equals(databaseName)
        && columnsMatch(table, columnTypes);
  }

  /** Checks whether the {@link Table} schema matches the given column schema. */
  private boolean columnsMatch(final Table table, final List<ColumnDataType> columnTypes) {
    return table
        .getColumns()
        .values()
        .stream()
        .map(ColumnMetadata::getColType)
        .collect(Collectors.toList())
        .equals(columnTypes);
  }

  private Table fetchTable(
      final long tableId,
      final String databaseName,
      final String tableName,
      final List<ColumnDataType> columnTypes)
      throws Exception {
    final List<MysqlColumn> tableSchema = schemaManager.getTableColumns(databaseName, tableName);
    final Iterator<MysqlColumn> schemaIterator = tableSchema.iterator();

    if (tableSchema.size() != columnTypes.size()) {
      log.error(
          "Schema length {} and Column length {} don't match",
          tableSchema.size(),
          columnTypes.size());
    }

    final List<ColumnMetadata> columnMetadata = new ArrayList<>();
    for (int position = 0; position < columnTypes.size() && schemaIterator.hasNext(); position++) {
      MysqlColumn colInfo = schemaIterator.next();
      columnMetadata.add(
          new ColumnMetadata(
              colInfo.getName(), columnTypes.get(position), colInfo.isPrimaryKey(), position, colInfo.getColumnType()));
    }

    final List<String> primaryColumns =
        tableSchema
            .stream()
            .filter(MysqlColumn::isPrimaryKey)
            .map(MysqlColumn::getName)
            .collect(Collectors.toList());

    return new Table(
        tableId, tableName, databaseName, overridingDatabase, columnMetadata, primaryColumns);
  }
}
