/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.source.SourceMetrics;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for metrics collection on operations for {@link MysqlSource} and associated
 * components.
 */
public class MysqlSourceMetrics extends SourceMetrics {
  private static final String MYSQL_PREFIX = METRIC_PREFIX + ".binlog";

  private static final String TRANSACTION_RECEIVED_METRIC = MYSQL_PREFIX + ".transaction.count";

  private static final String DESERIALIZATION_FAILURE_METRIC =
      MYSQL_PREFIX + ".deserialization.failure.count";
  private static final String COMMUNICATION_FAILURE_METRIC = MYSQL_PREFIX + ".comm.failure.count";

  private static final String CLIENT_CONNECTED_METRIC = MYSQL_PREFIX + ".connect.count";
  private static final String CLIENT_DISCONNECTED_METRIC = MYSQL_PREFIX + ".disconnect.count";

  private static final String SCHEMA_STORE_GET_SUCCESS_METRIC =
      MYSQL_PREFIX + ".schema_store.get.success.count";
  private static final String SCHEMA_STORE_GET_FAILURE_METRIC =
      MYSQL_PREFIX + ".schema_store.get.failure.count";

  private static final String SCHEMA_STORE_PUT_SUCCESS_METRIC =
      MYSQL_PREFIX + ".schema_store.put.success.count";
  private static final String SCHEMA_STORE_PUT_FAILURE_METRIC =
      MYSQL_PREFIX + ".schema_store.put.failure.count";

  private static final String SCHEMA_DATABASE_APPLY_DDL_SUCCESS_METRIC =
      MYSQL_PREFIX + ".schema_database.apply.ddl.success.count";
  private static final String SCHEMA_DATABASE_APPLY_DDL_FAILURE_METRIC =
      MYSQL_PREFIX + ".schema_database.apply.ddl.failure.count";

  private static final String DDL_HISTORY_STORE_GET_SUCCESS_METRIC =
      MYSQL_PREFIX + ".ddl_history_store.get.success.count";
  private static final String DDL_HISTORY_STORE_GET_FAILURE_METRIC =
      MYSQL_PREFIX + ".ddl_history_store.get.failure.count";

  private static final String DDL_HISTORY_STORE_PUT_SUCCESS_METRIC =
      MYSQL_PREFIX + ".ddl_history_store.put.success.count";
  private static final String DDL_HISTORY_STORE_PUT_FAILURE_METRIC =
      MYSQL_PREFIX + ".ddl_history_store.put.failure.count";

  private static final String INVALID_SCHEMA_METRIC = MYSQL_PREFIX + ".table.invalid_schema.count";
  private static final String BINLOG_FILE_START_METRIC = MYSQL_PREFIX + ".binlog_file.start.count";

  private static final String SAVE_STATE_METRIC = MYSQL_PREFIX + ".state.save.count";
  private static final String READ_STATE_METRIC = MYSQL_PREFIX + ".state.read.count";

  private static final String SAVE_STATE_FAILURE_METRIC =
      MYSQL_PREFIX + ".state.save.failure.count";
  private static final String READ_STATE_FAILURE_METRIC =
      MYSQL_PREFIX + ".state.read.failure.count";

  private static final String RESET_POSITION_METRIC = MYSQL_PREFIX + ".reset.position.count";
  private static final String RESET_EARLIEST_POSITION_METRIC =
      MYSQL_PREFIX + ".reset.earliest_position.count";

  public MysqlSourceMetrics(final String sourceName, final TaggedMetricRegistry metricRegistry) {
    this(sourceName, "mysql", metricRegistry);
  }

  protected MysqlSourceMetrics(
      String sourceName, String sourceType, TaggedMetricRegistry metricRegistry) {
    super(sourceName, sourceType, metricRegistry);
  }

  public void communicationFailure(Throwable error) {
    incError(COMMUNICATION_FAILURE_METRIC, error);
  }

  public void deserializationFailure(Throwable error) {
    incError(DESERIALIZATION_FAILURE_METRIC, error);
  }

  public void clientConnected() {
    inc(CLIENT_CONNECTED_METRIC);
  }

  public void clientDisconnected() {
    inc(CLIENT_DISCONNECTED_METRIC);
  }

  public void schemaStoreGetSuccess(final String database, final String table) {
    inc(SCHEMA_STORE_GET_SUCCESS_METRIC, getTableTags(database, table));
  }

  public void schemaStoreGetFailure(
      final String database, final String table, final Throwable error) {
    incError(SCHEMA_STORE_GET_FAILURE_METRIC, error, getTableTags(database, table));
  }

  public void schemaStoreGetSuccess(final String database) {
    inc(SCHEMA_STORE_GET_SUCCESS_METRIC, ImmutableMap.of(DATABASE_NAME_TAG, database));
  }

  public void schemaStoreGetFailure(final String database, final Throwable error) {
    incError(SCHEMA_STORE_GET_FAILURE_METRIC, error, ImmutableMap.of(DATABASE_NAME_TAG, database));
  }

  public void schemaStorePutSuccess(final String database, final String table) {
    inc(SCHEMA_STORE_PUT_SUCCESS_METRIC, getTableTags(database, table));
  }

  public void schemaStorePutFailure(
      final String database, final String table, final Throwable error) {
    incError(SCHEMA_STORE_PUT_FAILURE_METRIC, error, getTableTags(database, table));
  }

  public void schemaDatabaseApplyDDLSuccess(final String database) {
    inc(SCHEMA_DATABASE_APPLY_DDL_SUCCESS_METRIC, ImmutableMap.of(DATABASE_NAME_TAG, database));
  }

  public void schemaDatabaseApplyDDLFailure(final String database, final Throwable error) {
    incError(
        SCHEMA_DATABASE_APPLY_DDL_FAILURE_METRIC,
        error,
        ImmutableMap.of(DATABASE_NAME_TAG, database));
  }

  public void ddlHistoryStorePutSuccess() {
    inc(DDL_HISTORY_STORE_PUT_SUCCESS_METRIC);
  }

  public void ddlHistoryStorePutFailure(final Throwable error) {
    incError(DDL_HISTORY_STORE_PUT_FAILURE_METRIC, error);
  }

  public void ddlHistoryStoreGetSuccess() {
    inc(DDL_HISTORY_STORE_GET_SUCCESS_METRIC);
  }

  public void ddlHistoryStoreGetFailure(final Throwable error) {
    incError(DDL_HISTORY_STORE_GET_FAILURE_METRIC, error);
  }

  public void invalidSchema(final Mutation<?> mutation) {
    inc(INVALID_SCHEMA_METRIC, getTags(mutation));
  }

  public void binlogFileStart() {
    inc(BINLOG_FILE_START_METRIC);
  }

  public void stateSave() {
    inc(SAVE_STATE_METRIC);
  }

  public void stateRead() {
    inc(READ_STATE_METRIC);
  }

  public void stateSaveFailure(Throwable error) {
    incError(SAVE_STATE_FAILURE_METRIC, error);
  }

  public void stateReadFailure(Throwable error) {
    incError(READ_STATE_FAILURE_METRIC, error);
  }

  public void resetSourcePosition() {
    inc(RESET_POSITION_METRIC);
  }

  public void resetEarliestPosition() {
    inc(RESET_EARLIEST_POSITION_METRIC);
  }

  public void transactionReceived() {
    inc(TRANSACTION_RECEIVED_METRIC);
  }

  private Map<String, String> getTableTags(final String database, final String table) {
    Map<String, String> tableTags = new HashMap<>();

    tableTags.put(DATABASE_NAME_TAG, database);
    tableTags.put(TABLE_NAME_TAG, table);

    return tableTags;
  }
}
