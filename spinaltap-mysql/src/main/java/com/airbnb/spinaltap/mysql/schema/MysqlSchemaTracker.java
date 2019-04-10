/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Represents a {@link SchemaTracker} that tracks MySQL schema change. */
@Slf4j
@RequiredArgsConstructor
public class MysqlSchemaTracker implements SchemaTracker {
  private static final Pattern DATABASE_DDL_SQL_PATTERN =
      Pattern.compile("^(CREATE|DROP)\\s+(DATABASE|SCHEMA)", Pattern.CASE_INSENSITIVE);
  private final SchemaStore<MysqlTableSchema> schemaStore;
  private final MysqlSchemaDatabase schemaDatabase;
  private final MysqlDDLHistoryStore ddlHistoryStore;

  public void processDDLStatement(@NotNull final QueryEvent event) {
    BinlogFilePos binlogFilePos = event.getBinlogFilePos();
    String ddl = event.getSql();

    if (schemaStore.get(binlogFilePos) != null || ddlHistoryStore.get(binlogFilePos) != null) {
      log.info(
          String.format(
              "DDL Statement (%s) has already been processed. (BinlogFilePos: %s)",
              ddl, binlogFilePos));
      return;
    }

    // It could be a new database which has not been created in schema store database, so don't
    // switch to any database before applying database DDL.
    schemaDatabase.applyDDLStatement(
        DATABASE_DDL_SQL_PATTERN.matcher(ddl).find() ? "" : event.getDatabase(), ddl);

    // Get schemas for active tables in schema store
    Table<String, String, MysqlTableSchema> activeTableSchemasInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    schemaStore
        .getAll()
        .values()
        .stream()
        .map(treeMap -> treeMap.lastEntry().getValue())
        .filter(
            schema ->
                !schema
                    .getColumnInfo()
                    .isEmpty()) // filter out deleted tables whose columnInfo is an empty list
        .forEach(
            schema ->
                activeTableSchemasInStore.put(schema.getDatabase(), schema.getTable(), schema));

    Set<String> activeDatabasesInStore = activeTableSchemasInStore.rowKeySet();
    Set<String> databasesInSchemaDatabase = schemaDatabase.listDatabases();

    // Handle new databases
    Sets.difference(databasesInSchemaDatabase, activeDatabasesInStore)
        .forEach(
            newDatabase ->
                updateSchemaStore(
                    newDatabase,
                    event,
                    Maps.newHashMap(),
                    schemaDatabase.fetchTableSchema(newDatabase)));
    // Handle existing databases
    activeDatabasesInStore.forEach(
        database ->
            updateSchemaStore(
                database,
                event,
                activeTableSchemasInStore.row(database),
                schemaDatabase.fetchTableSchema(database)));

    log.info("Saving DDL into History Store: {}", ddl);
    ddlHistoryStore.put(binlogFilePos, ddl, event.getTimestamp());
  }

  private void updateSchemaStore(
      @NotNull final String database,
      @NotNull final QueryEvent event,
      @NotNull final Map<String, MysqlTableSchema> tableSchemaMapInStore,
      @NotNull final Map<String, MysqlTableSchema> tableSchemaMapInSchemaDatabase) {
    // Handle deleted tables
    Sets.difference(tableSchemaMapInStore.keySet(), tableSchemaMapInSchemaDatabase.keySet())
        .forEach(
            table ->
                schemaStore.put(
                    database,
                    table,
                    event.getBinlogFilePos(),
                    event.getTimestamp(),
                    event.getSql(),
                    Lists.newArrayList()));
    // Handle new/updated tables
    tableSchemaMapInSchemaDatabase.forEach(
        (table, mysqlTableSchema) -> {
          if (!tableSchemaMapInStore.containsKey(table)
              || !tableSchemaMapInStore
                  .get(table)
                  .getColumnInfo()
                  .equals(mysqlTableSchema.getColumnInfo())) {
            schemaStore.put(
                database,
                table,
                event.getBinlogFilePos(),
                event.getTimestamp(),
                event.getSql(),
                mysqlTableSchema.getColumnInfo());
          }
        });
  }
}
