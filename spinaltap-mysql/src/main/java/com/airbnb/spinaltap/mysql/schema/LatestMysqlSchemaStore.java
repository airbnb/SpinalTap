/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.StringColumnMapper;

/**
 * Represents the current(latest) snapshot of MySQL schema. Schema queries will hit MySQL
 * information_schema.
 */
@Slf4j
public class LatestMysqlSchemaStore extends AbstractMysqlSchemaStore
    implements SchemaStore<MysqlTableSchema> {
  private static final String TABLE_SCHEMA_QUERY =
      "select TABLE_NAME, COLUMN_NAME, COLUMN_KEY, COLUMN_TYPE from information_schema.COLUMNS "
          + "where TABLE_SCHEMA = :db and TABLE_NAME = :table "
          + "order by ORDINAL_POSITION";
  private static final String ALL_TABLE_SCHEMA_QUERY =
      "select TABLE_NAME, COLUMN_NAME, COLUMN_KEY, COLUMN_TYPE from information_schema.COLUMNS "
          + "where TABLE_SCHEMA = :db "
          + "order by ORDINAL_POSITION";
  private static final String LIST_ALL_TABLES_QUERY =
      "select TABLE_NAME from information_schema.TABLES "
          + "where TABLE_SCHEMA = :db and TABLE_TYPE = 'BASE TABLE'";
  private static final String LIST_ALL_DATABASES_QUERY =
      "select SCHEMA_NAME from information_schema.SCHEMATA";
  private static final String TABLE_DDL_QUERY = "SHOW CREATE TABLE `%s`.`%s`";

  private final DBI jdbi;
  private final MysqlSourceMetrics metrics;

  public LatestMysqlSchemaStore(
      @NotNull final String source,
      @NotNull final DBI jdbi,
      @NotNull final MysqlSourceMetrics metrics) {
    super(source, metrics);
    this.jdbi = jdbi;
    this.metrics = metrics;
  }

  @Override
  public void put(@NotNull final MysqlTableSchema schema) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MysqlTableSchema query(
      @NotNull final String database,
      @NotNull final String table,
      @NotNull final BinlogFilePos binlogFilePos) {
    return getLatest(database, table);
  }

  @Override
  public MysqlTableSchema get(
      @NotNull final String database,
      @NotNull final String table,
      @NotNull final int schemaVersion) {
    return getLatest(database, table);
  }

  @Override
  public MysqlTableSchema getLatest(@NotNull final String database, @NotNull final String table) {
    try (Handle handle = jdbi.open()) {
      log.info("Fetching schema for table {}, database {}", table, database);
      List<ColumnInfo> columnInfoList =
          MysqlSchemaUtil.LIST_COLUMNINFO_RETRYER.call(
              () ->
                  handle
                      .createQuery(TABLE_SCHEMA_QUERY)
                      .bind("db", database)
                      .bind("table", table)
                      .map(MysqlSchemaUtil.COLUMN_MAPPER)
                      .list());

      metrics.schemaStoreGetSuccess(database, table);

      return MysqlSchemaUtil.createTableSchema(
          source, database, table, getTableDDL(database, table), columnInfoList);
    } catch (Exception ex) {
      log.error(String.format("Failed to fetch schema for table %s, db %s", table, database), ex);
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Map<String, MysqlTableSchema> getLatest(@NotNull final String database) {
    List<ColumnInfo> allColumnInfo;
    try (Handle handle = jdbi.open()) {
      allColumnInfo =
          MysqlSchemaUtil.LIST_COLUMNINFO_RETRYER.call(
              () ->
                  handle
                      .createQuery(ALL_TABLE_SCHEMA_QUERY)
                      .bind("db", database)
                      .map(MysqlSchemaUtil.COLUMN_MAPPER)
                      .list());
    } catch (Exception ex) {
      log.error(String.format("Failed to fetch schema for database: %s", database), ex);
      throw new RuntimeException(ex);
    }
    Map<String, MysqlTableSchema> allTableSchemaMap = Maps.newHashMap();

    allColumnInfo.forEach(
        columnInfo -> {
          String table = columnInfo.getTable();
          allTableSchemaMap
              .computeIfAbsent(
                  table,
                  __ ->
                      MysqlSchemaUtil.createTableSchema(
                          source,
                          database,
                          table,
                          getTableDDL(database, table),
                          Lists.newArrayList()))
              .getColumnInfo()
              .add(columnInfo);
        });

    return allTableSchemaMap;
  }

  @Override
  public int getLatestVersion(@NotNull final String database, @NotNull final String table) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Table<String, String, TreeMap<Integer, MysqlTableSchema>> getAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TreeMap<Integer, MysqlTableSchema> getAll(
      @NotNull final String database, @NotNull final String table) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean exists(@NotNull final String database, @NotNull final String table) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MysqlTableSchema get(@NotNull final BinlogFilePos binlogFilePos) {
    throw new UnsupportedOperationException();
  }

  List<String> listAllDatabases() {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_STRING_RETRYER.call(
          () ->
              handle.createQuery(LIST_ALL_DATABASES_QUERY).map(StringColumnMapper.INSTANCE).list());
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to list all databases for source: %s. (Exception: %s)", source, ex));
      throw new RuntimeException(ex);
    }
  }

  List<String> listAllTables(@NotNull final String database) {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_STRING_RETRYER.call(
          () ->
              handle
                  .createQuery(LIST_ALL_TABLES_QUERY)
                  .bind("db", database)
                  .map(StringColumnMapper.INSTANCE)
                  .list());
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to list all tables for database: %s. (Exception: %s)", database, ex));
      throw new RuntimeException(ex);
    }
  }

  String getTableDDL(@NotNull final String database, @NotNull final String table) {
    // Use JDBC API here because parameter binding does not work for `SHOW CREATE TABLE` in JDBI
    // and handle.createQuery() needs to escape colon(:)
    try (Handle handle = jdbi.open()) {
      Statement statement = handle.getConnection().createStatement();
      statement.execute(
          String.format(
              TABLE_DDL_QUERY,
              MysqlSchemaUtil.escapeBackQuote(database),
              MysqlSchemaUtil.escapeBackQuote(table)));
      ResultSet resultSet = statement.getResultSet();
      resultSet.first();
      return resultSet.getString(2);
    } catch (SQLException ex) {
      log.error(
          String.format("Failed to get DDL for database: %s table: %s.", database, table), ex);
      throw new RuntimeException(ex);
    }
  }
}
