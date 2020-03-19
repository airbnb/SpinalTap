/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import com.airbnb.jitney.event.spinaltap.v1.Column;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;

/** Represents a MySQL table. */
@Value
public final class Table {
  private final long id;
  private final String name;
  private final String database;
  private final String overridingDatabase;

  /**
   * Note: It is important that the implement of the columns map retains the order of entry
   * insertion, as the sequence in which row columns in the binlog event are extracted is dependent
   * on it. {@code ImmutableMap} does retain the ordering. Any changes should should take this into
   * consideration
   */
  private final ImmutableMap<String, ColumnMetadata> columns;

  private final Optional<PrimaryKey> primaryKey;

  @Getter(lazy = true)
  private final com.airbnb.jitney.event.spinaltap.v1.Table thriftTable = toThriftTable(this);

  public Table(
      long id,
      String name,
      String database,
      List<ColumnMetadata> columnMetadatas,
      List<String> primaryKeyColumns) {
    this(id, name, database, null, columnMetadatas, primaryKeyColumns);
  }

  public Table(
      long id,
      String name,
      String database,
      String overridingDatabase,
      List<ColumnMetadata> columnMetadatas,
      List<String> primaryKeyColumns) {
    this.id = id;
    this.name = name;
    this.database = database;
    this.overridingDatabase = overridingDatabase;
    this.columns = createColumns(columnMetadatas);
    this.primaryKey = createPrimaryKey(primaryKeyColumns, columns);
  }

  public static com.airbnb.jitney.event.spinaltap.v1.Table toThriftTable(Table table) {
    Set<String> primaryKey = ImmutableSet.of();
    if (table.getPrimaryKey().isPresent()) {
      primaryKey =
          ImmutableSet.copyOf(
              table
                  .getPrimaryKey()
                  .get()
                  .getColumns()
                  .values()
                  .stream()
                  .map(ColumnMetadata::getName)
                  .sorted()
                  .collect(Collectors.toList()));
    }

    Map<String, Column> columns =
        table
            .getColumns()
            .values()
            .stream()
            .map(
                c -> {
                  com.airbnb.jitney.event.spinaltap.v1.Column column =
                      new com.airbnb.jitney.event.spinaltap.v1.Column(
                          c.getColType().getCode(), c.isPrimaryKey(), c.getName());

                  column.setPosition(c.getPosition());

                  return column;
                })
            .collect(Collectors.toMap(c -> c.getName(), c -> c));

    com.airbnb.jitney.event.spinaltap.v1.Table thriftTable =
        new com.airbnb.jitney.event.spinaltap.v1.Table(
            table.getId(), table.getName(), table.getDatabase(), primaryKey, columns);
    if (!Strings.isNullOrEmpty(table.getOverridingDatabase())) {
      thriftTable.setOverridingDatabase(table.getOverridingDatabase());
    }
    return thriftTable;
  }

  public static String canonicalNameOf(String db, String tableName) {
    return String.format("%s:%s", db, tableName);
  }

  public static Set<String> getDatabaseNames(Collection<String> canonicalTableNames) {
    Set<String> databaseNames = Sets.newHashSet();

    canonicalTableNames.forEach(
        canonicalTableName -> {
          String databaseName = Splitter.on(':').split(canonicalTableName).iterator().next();
          databaseNames.add(databaseName);
        });

    return databaseNames;
  }

  public String getCanonicalName() {
    return canonicalNameOf(database, name);
  }

  private static Optional<PrimaryKey> createPrimaryKey(
      List<String> pkColumnNames, ImmutableMap<String, ColumnMetadata> columns) {
    if (pkColumnNames.isEmpty()) {
      return Optional.absent();
    }

    ImmutableMap.Builder<String, ColumnMetadata> builder = ImmutableMap.builder();
    for (String colName : pkColumnNames) {
      builder.put(colName, columns.get(colName));
    }

    return Optional.of(new PrimaryKey(builder.build()));
  }

  private static ImmutableMap<String, ColumnMetadata> createColumns(
      List<ColumnMetadata> columnMetadatas) {
    ImmutableMap.Builder<String, ColumnMetadata> builder = ImmutableMap.builder();
    for (ColumnMetadata col : columnMetadatas) {
      builder.put(col.getName(), col);
    }
    return builder.build();
  }
}
