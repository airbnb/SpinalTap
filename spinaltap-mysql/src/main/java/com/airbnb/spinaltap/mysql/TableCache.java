/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.airbnb.spinaltap.mysql.schema.ColumnInfo;
import com.airbnb.spinaltap.mysql.schema.MysqlTableSchema;
import com.airbnb.spinaltap.mysql.schema.SchemaStore;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Caches table schema and information used during transformation of mysql events to Mutations. */
@Slf4j
@RequiredArgsConstructor
public class TableCache {
  private final SchemaStore<MysqlTableSchema> schemaStore;
  private final Cache<Long, Table> tableCache = CacheBuilder.newBuilder().maximumSize(200).build();

  /** Gets the cached table entry for table ID, returns {@code null} if not present */
  public Table get(long tableId) {
    return tableCache.getIfPresent(tableId);
  }

  public boolean contains(long tableId) {
    return tableCache.getIfPresent(tableId) != null;
  }

  public void addOrUpdate(
      long tableId,
      String tableName,
      String database,
      BinlogFilePos binlogFilePos,
      List<ColumnDataType> columnTypes)
      throws Exception {
    Table table = tableCache.getIfPresent(tableId);

    if (table == null || !validTable(table, tableName, database, columnTypes)) {
      table = fetchTable(tableId, database, tableName, binlogFilePos, columnTypes);
      tableCache.put(tableId, table);
    }
  }

  /** Clear the cache */
  public void clear() {
    tableCache.invalidateAll();
  }

  private boolean validTable(
      Table table, String tableName, String databaseName, List<ColumnDataType> columnTypes) {
    return table.getName().equals(tableName)
        && table.getDatabase().equals(databaseName)
        && columnsMatch(table, columnTypes);
  }

  private boolean columnsMatch(Table table, List<ColumnDataType> columnTypes) {
    return table
        .getColumns()
        .values()
        .stream()
        .map(ColumnMetadata::getColType)
        .collect(Collectors.toList())
        .equals(columnTypes);
  }

  private Table fetchTable(
      long tableId,
      String databaseName,
      String tableName,
      BinlogFilePos binlogFilePos,
      List<ColumnDataType> columnTypes)
      throws Exception {
    List<ColumnInfo> tableSchema =
        schemaStore.query(databaseName, tableName, binlogFilePos).getColumnInfo();
    Iterator<ColumnInfo> schemaIterator = tableSchema.iterator();

    if (tableSchema.size() != columnTypes.size()) {
      log.error(
          "Schema length {} and Column length {} don't match",
          tableSchema.size(),
          columnTypes.size());
    }

    List<ColumnMetadata> columnMetadata = new ArrayList<>();
    for (int position = 0; position < columnTypes.size() && schemaIterator.hasNext(); position++) {
      ColumnInfo colInfo = schemaIterator.next();
      columnMetadata.add(
          new ColumnMetadata(
              colInfo.getName(), columnTypes.get(position), colInfo.isPrimaryKey(), position));
    }

    List<String> primaryColumns =
        tableSchema
            .stream()
            .filter(ColumnInfo::isPrimaryKey)
            .map(ColumnInfo::getName)
            .collect(Collectors.toList());

    return new Table(tableId, tableName, databaseName, columnMetadata, primaryColumns);
  }
}
