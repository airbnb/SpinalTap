/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.GtidSet;
import com.airbnb.spinaltap.mysql.MysqlClient;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MysqlSchemaManager implements MysqlSchemaArchiver {
  private static final Set<String> SYSTEM_DATABASES =
      ImmutableSet.of("mysql", "information_schema", "performance_schema", "sys");
  private static final Pattern DATABASE_DDL_SQL_PATTERN =
      Pattern.compile("^(CREATE|DROP)\\s+(DATABASE|SCHEMA)", Pattern.CASE_INSENSITIVE);
  private static final Pattern TABLE_DDL_SQL_PATTERN =
      Pattern.compile("^(ALTER|CREATE|DROP|RENAME)\\s+TABLE", Pattern.CASE_INSENSITIVE);
  private static final Pattern INDEX_DDL_SQL_PATTERN =
      Pattern.compile(
          "^((CREATE(\\s+(UNIQUE|FULLTEXT|SPATIAL))?)|DROP)\\s+INDEX", Pattern.CASE_INSENSITIVE);
  private static final Pattern GRANT_DDL_SQL_PATTERN =
      Pattern.compile("^GRANT\\s+", Pattern.CASE_INSENSITIVE);
  private final String sourceName;
  private final MysqlSchemaStore schemaStore;
  private final MysqlSchemaDatabase schemaDatabase;
  private final MysqlSchemaReader schemaReader;
  private final MysqlClient mysqlClient;
  private final boolean isSchemaVersionEnabled;

  public List<MysqlColumn> getTableColumns(String database, String table) {
    return isSchemaVersionEnabled
        ? schemaStore.get(database, table).getColumns()
        : schemaReader.getTableColumns(database, table);
  }

  public void processDDL(QueryEvent event, String gtid) {
    String sql = event.getSql();
    BinlogFilePos pos = event.getBinlogFilePos();
    String database = event.getDatabase();
    if (!isSchemaVersionEnabled) {
      if (isDDLGrant(sql)) {
        log.info("Skip processing a Grant DDL because schema versioning is not enabled.");
      } else {
        log.info("Skip processing DDL {} because schema versioning is not enabled.", sql);
      }
      return;
    }

    if (!shouldProcessDDL(sql)) {
      if (isDDLGrant(sql)) {
        log.info("Not processing a Grant DDL because it is not our interest.");
      } else {
        log.info("Not processing DDL {} because it is not our interest.", sql);
      }
      return;
    }

    // Check if this schema change was processed before
    List<MysqlTableSchema> schemas =
        gtid == null ? schemaStore.queryByBinlogFilePos(pos) : schemaStore.queryByGTID(gtid);
    if (!schemas.isEmpty()) {
      log.info("DDL {} is already processed at BinlogFilePos: {}, GTID: {}", sql, pos, gtid);
      schemas.forEach(schemaStore::updateSchemaCache);
      return;
    }

    String databaseToUse = database;
    // Set database to be null in following 2 cases:
    // 1. It could be a new database which has not been created in schema store database, so don't
    //   switch to any database before applying database DDL.
    // 2. It could be a system database while DDL uses the fully qualified table name (db.table).
    //   E.g. User can apply DDL "CREATE TABLE DB.TABLE xxx" while the database set in current
    // session is "sys".
    // In either case, `addSourcePrefix` inside `applyDDL` will add the source prefix to the
    // database name
    // (sourceName/databaseName) so that it will be properly tracked in schema database
    if (DATABASE_DDL_SQL_PATTERN.matcher(sql).find() || SYSTEM_DATABASES.contains(database)) {
      databaseToUse = null;
    }
    schemaDatabase.applyDDL(sql, databaseToUse);

    // See what changed, check database by database
    Set<String> databasesInSchemaStore =
        ImmutableSet.copyOf(schemaStore.getSchemaCache().rowKeySet());
    Set<String> databasesInSchemaDatabase = ImmutableSet.copyOf(schemaDatabase.listDatabases());
    boolean isTableColumnsChanged = false;

    for (String newDatabase : Sets.difference(databasesInSchemaDatabase, databasesInSchemaStore)) {
      boolean isColumnChangedForNewDB =
          processTableSchemaChanges(
              newDatabase,
              event,
              gtid,
              Collections.emptyMap(),
              schemaDatabase.getColumnsForAllTables(newDatabase));
      isTableColumnsChanged = isTableColumnsChanged || isColumnChangedForNewDB;
    }

    for (String existingDatbase : databasesInSchemaStore) {
      boolean isColumnChangedForExistingDB =
          processTableSchemaChanges(
              existingDatbase,
              event,
              gtid,
              schemaStore.getSchemaCache().row(existingDatbase),
              schemaDatabase.getColumnsForAllTables(existingDatbase));
      isTableColumnsChanged = isTableColumnsChanged || isColumnChangedForExistingDB;
    }

    if (!isTableColumnsChanged) {
      // if the schema store is not updated, most likely the DDL does not change table columns.
      // we need to update schema store here to keep a record, so the DDL won't be processed again
      schemaStore.put(
          new MysqlTableSchema(
              0,
              database,
              null,
              pos,
              gtid,
              sql,
              event.getTimestamp(),
              Collections.emptyList(),
              Collections.emptyMap()));
    }
  }

  private boolean processTableSchemaChanges(
      String database,
      QueryEvent event,
      String gtid,
      Map<String, MysqlTableSchema> tableSchemaMapInSchemaStore,
      Map<String, List<MysqlColumn>> tableColumnsInSchemaDatabase) {
    boolean isTableColumnChanged = false;

    Set<String> deletedTables =
        Sets.difference(tableSchemaMapInSchemaStore.keySet(), tableColumnsInSchemaDatabase.keySet())
            .immutableCopy();
    for (String deletedTable : deletedTables) {
      schemaStore.put(
          new MysqlTableSchema(
              0,
              database,
              deletedTable,
              event.getBinlogFilePos(),
              gtid,
              event.getSql(),
              event.getTimestamp(),
              Collections.emptyList(),
              Collections.emptyMap()));
      isTableColumnChanged = true;
    }

    for (Map.Entry<String, List<MysqlColumn>> tableColumns :
        tableColumnsInSchemaDatabase.entrySet()) {
      String table = tableColumns.getKey();
      List<MysqlColumn> columns = tableColumns.getValue();
      if (!tableSchemaMapInSchemaStore.containsKey(table)
          || !columns.equals(tableSchemaMapInSchemaStore.get(table).getColumns())) {
        schemaStore.put(
            new MysqlTableSchema(
                0,
                database,
                table,
                event.getBinlogFilePos(),
                gtid,
                event.getSql(),
                event.getTimestamp(),
                columns,
                Collections.emptyMap()));
        isTableColumnChanged = true;
      }
    }
    return isTableColumnChanged;
  }

  public synchronized void initialize(BinlogFilePos pos) {
    if (!isSchemaVersionEnabled) {
      log.info("Schema versioning is not enabled for {}", sourceName);
      return;
    }
    if (schemaStore.isCreated()) {
      log.info(
          "Schema store for {} is already bootstrapped. Loading schemas to store till {}, GTID Set: {}",
          sourceName,
          pos,
          pos.getGtidSet());
      schemaStore.loadSchemaCacheUntil(pos);
      return;
    }

    log.info("Bootstrapping schema store for {}...", sourceName);
    BinlogFilePos earliestPos = new BinlogFilePos(mysqlClient.getBinaryLogs().get(0));
    earliestPos.setServerUUID(mysqlClient.getServerUUID());
    if (mysqlClient.isGtidModeEnabled()) {
      earliestPos.setGtidSet(new GtidSet(mysqlClient.getGlobalVariableValue("gtid_purged")));
    }

    List<MysqlTableSchema> allTableSchemas = new ArrayList<>();
    for (String database : schemaReader.getAllDatabases()) {
      if (SYSTEM_DATABASES.contains(database)) {
        log.info("Skipping tables for system database: {}", database);
        continue;
      }

      log.info("Bootstrapping table schemas for database {}", database);
      schemaDatabase.createDatabase(database);

      for (String table : schemaReader.getAllTablesIn(database)) {
        String createTableDDL = schemaReader.getCreateTableDDL(database, table);
        schemaDatabase.applyDDL(createTableDDL, database);
        allTableSchemas.add(
            new MysqlTableSchema(
                0,
                database,
                table,
                earliestPos,
                null,
                createTableDDL,
                System.currentTimeMillis(),
                schemaReader.getTableColumns(database, table),
                Collections.emptyMap()));
      }
    }
    schemaStore.bootstrap(allTableSchemas);
  }

  @Override
  public synchronized void archive() {
    if (!isSchemaVersionEnabled) {
      log.info("Schema versioning is not enabled for {}", sourceName);
      return;
    }
    schemaStore.archive();
    schemaDatabase.dropDatabases();
  }

  public void compress() {
    if (!isSchemaVersionEnabled) {
      log.info("Schema versioning is not enabled for {}", sourceName);
      return;
    }
    String purgedGTID = mysqlClient.getGlobalVariableValue("gtid_purged");
    BinlogFilePos earliestPosition = new BinlogFilePos(mysqlClient.getBinaryLogs().get(0));
    earliestPosition.setServerUUID(mysqlClient.getServerUUID());
    if (mysqlClient.isGtidModeEnabled()) {
      earliestPosition.setGtidSet(new GtidSet(purgedGTID));
    }
    schemaStore.compress(earliestPosition);
  }

  private static boolean shouldProcessDDL(final String sql) {
    return TABLE_DDL_SQL_PATTERN.matcher(sql).find()
        || INDEX_DDL_SQL_PATTERN.matcher(sql).find()
        || DATABASE_DDL_SQL_PATTERN.matcher(sql).find();
  }

  private static boolean isDDLGrant(final String sql) {
    return GRANT_DDL_SQL_PATTERN.matcher(sql).find();
  }
}
