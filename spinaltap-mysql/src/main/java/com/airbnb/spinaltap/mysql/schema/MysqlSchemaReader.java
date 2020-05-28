/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

@Slf4j
@RequiredArgsConstructor
public class MysqlSchemaReader {
  private final String sourceName;
  private final Jdbi jdbi;
  private final MysqlSourceMetrics metrics;

  List<String> getAllDatabases() {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_STRING_RETRYER.call(
          () ->
              handle
                  .createQuery("select SCHEMA_NAME from information_schema.SCHEMATA")
                  .mapTo(String.class)
                  .list());
    } catch (Exception ex) {
      log.error(String.format("Failed to get all databases on %s, exception: %s", sourceName, ex));
      throw new RuntimeException(ex);
    }
  }

  List<String> getAllTablesIn(String database) {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_STRING_RETRYER.call(
          () ->
              handle
                  .createQuery(
                      "select TABLE_NAME from information_schema.TABLES where TABLE_SCHEMA = :db and TABLE_TYPE = 'BASE TABLE'")
                  .bind("db", database)
                  .mapTo(String.class)
                  .list());
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to get all tables in database %s on %s, exception: %s",
              database, sourceName, ex));
      throw new RuntimeException(ex);
    }
  }

  public List<MysqlColumn> getTableColumns(@NonNull String database, @NonNull String table) {
    try (Handle handle = jdbi.open()) {
      List<MysqlColumn> columns =
          MysqlSchemaUtil.LIST_COLUMN_RETRYER.call(
              () ->
                  handle
                      .createQuery(
                          "select COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, COLUMN_KEY from information_schema.COLUMNS "
                              + "where TABLE_SCHEMA = :db and TABLE_NAME = :table "
                              + "order by ORDINAL_POSITION")
                      .bind("db", database)
                      .bind("table", table)
                      .map(
                          (rs, ctx) ->
                              new MysqlColumn(
                                  rs.getString("COLUMN_NAME"),
                                  rs.getString("DATA_TYPE"),
                                  rs.getString("COLUMN_TYPE"),
                                  "PRI".equals(rs.getString("COLUMN_KEY"))))
                      .list());
      metrics.schemaStoreGetSuccess(database, table);
      return columns;
    } catch (Exception ex) {
      log.error(String.format("Failed to fetch schema for table %s, db %s", table, database), ex);
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  String getCreateTableDDL(@NonNull String database, @NonNull String table) {
    return jdbi.withHandle(
        handle -> {
          try {
            Statement statement = handle.getConnection().createStatement();
            statement.execute(
                String.format(
                    "SHOW CREATE TABLE `%s`.`%s`",
                    MysqlSchemaUtil.escapeBackQuote(database),
                    MysqlSchemaUtil.escapeBackQuote(table)));
            ResultSet resultSet = statement.getResultSet();
            resultSet.first();
            return resultSet.getString(2);
          } catch (SQLException ex) {
            log.error(
                String.format("Failed to get DDL for database: %s table: %s.", database, table),
                ex);
            throw new RuntimeException(ex);
          }
        });
  }
}
