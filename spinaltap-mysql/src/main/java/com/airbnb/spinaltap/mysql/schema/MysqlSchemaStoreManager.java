/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MysqlSchemaStoreManager implements SchemaStoreBootstrapper, SchemaStoreArchiver {
  private static final Set<String> SYSTEM_DATABASES =
      ImmutableSet.of("mysql", "information_schema", "performance_schema");

  @NonNull private final String source;
  @NonNull private final LatestMysqlSchemaStore schemaReader;
  @NonNull private final MysqlSchemaStore schemaStore;
  @NonNull private final MysqlSchemaDatabase schemaDatabase;
  @NonNull private final MysqlDDLHistoryStore ddlHistoryStore;

  public void bootstrap(@NotNull final String database) {
    Preconditions.checkState(
        schemaStore.isCreated(), "Schema store must be bootstrapped before adding a new database.");
    log.info("Bootstrapping schema store for source:{} database:{}", source, database);
    schemaDatabase.createDatabase(database);
    // For system databases, create them in schema database but don't bootstrap schema store for
    // their tables.
    if (SYSTEM_DATABASES.contains(database)) {
      log.info("Skipping tables for system database: {}", database);
      return;
    }
    schemaReader
        .listAllTables(database)
        .forEach(
            table -> {
              log.info("Bootstrapping schema store for table {}", table);
              schemaDatabase.applyDDLStatement(database, schemaReader.getTableDDL(database, table));
              schemaStore.put(schemaReader.getLatest(database, table));
            });
  }

  public void bootstrapAll() {
    Preconditions.checkState(
        !schemaStore.isCreated(),
        String.format(
            "%s seems to be bootstrapped already. Please archive it first if you would like to bootstrap again.",
            source));

    ddlHistoryStore.create();
    schemaStore.create();
    schemaReader.listAllDatabases().forEach(this::bootstrap);
  }

  public void archive(@NotNull final String database) {
    Preconditions.checkState(
        schemaStore.isCreated(), "Schema store must be bootstrapped before removing a database.");
    schemaDatabase.dropDatabase(String.format("%s.%s", source, database));
    schemaStore.archive(database);
  }

  public void archiveAll() {
    Preconditions.checkState(
        schemaStore.isCreated(),
        String.format("Unable to find schema store table for %s. Is it bootstrapped?", source));

    log.info("Archiving schema store for {}", source);
    schemaDatabase.dropDatabases();
    schemaStore.archive();
    ddlHistoryStore.archive();
  }
}
