/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.skife.jdbi.v2.DBI;

/**
 * Represents a cached implementation of {@link com.airbnb.spinaltap.mysql.schema.SchemaStore}. This
 * class acts as a proxy for {@link com.airbnb.spinaltap.mysql.schema.MysqlSchemaStore} and caches
 * the schema information in memory
 */
@Slf4j
public class CachedMysqlSchemaStore extends AbstractMysqlSchemaStore
    implements SchemaStore<MysqlTableSchema> {
  private static final int MAX_CACHE_SIZE = 10;
  private static final long EXPIRATION_TIME_IN_MILLIS = 3 * 24 * 3600 * 1000L; // 3 days
  private final MysqlSchemaStore schemaStore;
  // Row: database, Column: table, TreeMap key: BinlogFilePos
  private final Table<String, String, TreeMap<BinlogFilePos, MysqlTableSchema>>
      schemaBinlogPositionTable = Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
  // Row: database, Column: table, TreeMap key: Schema version
  private Table<String, String, TreeMap<Integer, MysqlTableSchema>> schemaVersionTable =
      Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);

  public CachedMysqlSchemaStore(
      @NotNull final String source,
      @NotNull final MysqlSchemaStore mysqlSchemaStore,
      @NotNull final MysqlSourceMetrics metrics) {
    super(source, metrics);
    this.schemaStore = mysqlSchemaStore;
    initialize();
  }

  public CachedMysqlSchemaStore(
      @NotNull final String source,
      @NotNull final DBI jdbi,
      @NotNull final String archiveDatabase,
      @NotNull final MysqlSourceMetrics metrics) {
    this(source, new MysqlSchemaStore(source, jdbi, archiveDatabase, metrics), metrics);
  }

  private void initialize() {
    try {
      schemaVersionTable = schemaStore.getAll();
    } catch (Exception ex) {
      log.error("Failed to populate cache.", ex);
      throw new RuntimeException(ex);
    }

    schemaVersionTable
        .values()
        .forEach(
            schemaVersionMap -> {
              invalidate(schemaVersionMap);
              schemaVersionMap.forEach(
                  (version, schemaInfo) -> {
                    String database = schemaInfo.getDatabase();
                    String table = schemaInfo.getTable();
                    if (!schemaBinlogPositionTable.contains(database, table)) {
                      schemaBinlogPositionTable.put(database, table, Maps.newTreeMap());
                    }
                    schemaBinlogPositionTable
                        .get(database, table)
                        .put(schemaInfo.getBinlogFilePos(), schemaInfo);
                  });
            });
  }

  private static <T> void invalidate(@NotNull final TreeMap<T, MysqlTableSchema> schemaMap) {
    while (schemaMap.size() > MAX_CACHE_SIZE) {
      Map.Entry<T, MysqlTableSchema> firstEntry = schemaMap.firstEntry();
      if (System.currentTimeMillis() - firstEntry.getValue().getTimestamp()
          <= EXPIRATION_TIME_IN_MILLIS) {
        break;
      }
      schemaMap.pollFirstEntry();
    }
  }

  @Override
  public void put(@NotNull final MysqlTableSchema schema) {
    String database = schema.getDatabase();
    String table = schema.getTable();
    BinlogFilePos binlogFilePos = schema.getBinlogFilePos();

    schemaStore.put(schema);

    if (!schemaVersionTable.contains(database, table)) {
      schemaVersionTable.put(database, table, Maps.newTreeMap());
      schemaBinlogPositionTable.put(database, table, Maps.newTreeMap());
    }

    TreeMap<Integer, MysqlTableSchema> schemaVersionMap = schemaVersionTable.get(database, table);
    TreeMap<BinlogFilePos, MysqlTableSchema> schemaTreeMap =
        schemaBinlogPositionTable.get(database, table);

    schemaVersionMap.put(schema.getVersion(), schema);
    schemaTreeMap.put(binlogFilePos, schema);

    invalidate(schemaVersionMap);
    invalidate(schemaTreeMap);
  }

  @Override
  public MysqlTableSchema query(
      @NotNull final String database,
      @NotNull final String table,
      @NotNull final BinlogFilePos binlogFilePos) {
    try {
      TreeMap<BinlogFilePos, MysqlTableSchema> tableSchemaTreeMap =
          Preconditions.checkNotNull(
              schemaBinlogPositionTable.get(database, table),
              String.format("No schema found for database: %s table: %s", database, table));

      BinlogFilePos key =
          Preconditions.checkNotNull(
              tableSchemaTreeMap.floorKey(binlogFilePos),
              String.format(
                  "No schema found for database: %s table: %s at binlog_pos: %s",
                  database, table, binlogFilePos));

      metrics.schemaStoreGetSuccess(database, table);

      return tableSchemaTreeMap.get(key);
    } catch (Exception ex) {
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public MysqlTableSchema get(
      @NotNull final String database, @NotNull final String table, final int version) {
    try {
      TreeMap<Integer, MysqlTableSchema> schemaVersionMap =
          Preconditions.checkNotNull(
              schemaVersionTable.get(database, table),
              String.format("No schema found for database: %s table: %s", database, table));

      Preconditions.checkArgument(
          schemaVersionMap.containsKey(version),
          String.format("Invalid schema version for database: %s table: %s", database, table));

      metrics.schemaStoreGetSuccess(database, table);

      return schemaVersionMap.get(version);
    } catch (Exception ex) {
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public MysqlTableSchema getLatest(@NotNull final String database, @NotNull final String table) {
    try {
      TreeMap<Integer, MysqlTableSchema> schemaVersionMap =
          Preconditions.checkNotNull(
              schemaVersionTable.get(database, table),
              String.format("No schema found for database: %s table: %s", database, table));

      metrics.schemaStoreGetSuccess(database, table);

      return schemaVersionMap.lastEntry().getValue();
    } catch (Exception ex) {
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Map<String, MysqlTableSchema> getLatest(@NotNull final String database) {
    try {
      Preconditions.checkArgument(
          schemaVersionTable.containsRow(database),
          String.format("No schema found for database: %s", database));

      Map<String, MysqlTableSchema> latestSchemaMap = Maps.newHashMap();

      schemaVersionTable
          .row(database)
          .forEach(
              (table, schemaVersionMap) ->
                  latestSchemaMap.put(table, schemaVersionMap.lastEntry().getValue()));

      metrics.schemaStoreGetSuccess(database);

      return latestSchemaMap;
    } catch (Exception ex) {
      metrics.schemaStoreGetFailure(database, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public int getLatestVersion(@NotNull final String database, @NotNull final String table) {
    try {
      TreeMap<Integer, MysqlTableSchema> schemaVersionMap =
          Preconditions.checkNotNull(
              schemaVersionTable.get(database, table),
              String.format("No schema found for database: %s table: %s", database, table));

      metrics.schemaStoreGetSuccess(database, table);

      return schemaVersionMap.lastKey();
    } catch (Exception ex) {
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Table<String, String, TreeMap<Integer, MysqlTableSchema>> getAll() {
    return schemaVersionTable;
  }

  @Override
  public TreeMap<Integer, MysqlTableSchema> getAll(
      @NotNull final String database, @NotNull final String table) {
    try {
      Preconditions.checkArgument(
          schemaVersionTable.contains(database, table),
          String.format("No schema found for database: %s table: %s", database, table));

      metrics.schemaStoreGetSuccess(database, table);

      return schemaVersionTable.get(database, table);
    } catch (Exception ex) {
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public MysqlTableSchema get(@NotNull final BinlogFilePos binlogFilePos) {
    return schemaBinlogPositionTable
        .values()
        .stream()
        .map(schemaMap -> schemaMap.get(binlogFilePos))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean exists(@NotNull final String database, @NotNull final String table) {
    return schemaVersionTable.contains(database, table);
  }
}
