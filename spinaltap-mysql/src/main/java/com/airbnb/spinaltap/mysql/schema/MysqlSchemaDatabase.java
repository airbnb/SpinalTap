/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

@Slf4j
@RequiredArgsConstructor
public class MysqlSchemaDatabase {
  private static final char DELIMITER = '/';

  private final String sourceName;
  private final Jdbi jdbi;
  private final MysqlSourceMetrics metrics;

  void applyDDL(@NonNull final String sql, final String database) {
    log.info(String.format("Applying DDL statement: %s (Database selected: %s)", sql, database));
    try (Handle handle = jdbi.open()) {
      handle.execute("SET foreign_key_checks=0");
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            MysqlSchemaUtil.executeWithJdbc(
                handle, getSchemaDatabaseName(sourceName, database), addSourcePrefix(sql));
            return null;
          });
      metrics.schemaDatabaseApplyDDLSuccess(database);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to apply DDL Statement to source: %s database: %s. (SQL: %s. Exception: %s)",
              sourceName, database, sql, ex));
      metrics.schemaDatabaseApplyDDLFailure(database, ex);
      throw new RuntimeException(ex);
    }
  }

  List<String> listDatabases() {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_STRING_RETRYER.call(
          () ->
              handle
                  .createQuery(
                      String.format(
                          "select SCHEMA_NAME from information_schema.SCHEMATA "
                              + "where SCHEMA_NAME LIKE '%s%s%%'",
                          sourceName, DELIMITER))
                  .mapTo(String.class)
                  .map(
                      database ->
                          database.replaceFirst(String.format("^%s%s", sourceName, DELIMITER), ""))
                  .list());
    } catch (Exception ex) {
      log.error(
          String.format("Failed to list databases for source: %s (Exception: %s)", sourceName, ex));
      throw new RuntimeException(ex);
    }
  }

  void createDatabase(@NonNull final String database) {
    log.info("Creating database: {}", database);
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            MysqlSchemaUtil.executeWithJdbc(
                handle,
                null,
                String.format(
                    "CREATE DATABASE `%s`",
                    getSchemaDatabaseName(sourceName, MysqlSchemaUtil.escapeBackQuote(database))));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to create database %s (Exception: %s)",
              getSchemaDatabaseName(sourceName, database), ex));
      throw new RuntimeException(ex);
    }
  }

  void dropDatabases() {
    listDatabases().forEach(this::dropDatabase);
  }

  void dropDatabase(@NonNull final String database) {
    log.info("Dropping database: {}", database);
    try (Handle handle = jdbi.open()) {
      handle.execute("SET foreign_key_checks=0");
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            MysqlSchemaUtil.executeWithJdbc(
                handle,
                null,
                String.format(
                    "DROP DATABASE IF EXISTS `%s`",
                    MysqlSchemaUtil.escapeBackQuote(getSchemaDatabaseName(sourceName, database))));
            return null;
          });
    } catch (Exception ex) {
      log.error(String.format("Failed to drop database %s. (Exception: %s)", database, ex));
      throw new RuntimeException(ex);
    }
  }

  Map<String, List<MysqlColumn>> getColumnsForAllTables(@NonNull String database) {
    try (Handle handle = jdbi.open()) {
      Map<String, List<MysqlColumn>> tableColumnsMap = new HashMap<>();
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle
                .createQuery(
                    "select TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, COLUMN_KEY from information_schema.COLUMNS "
                        + "where TABLE_SCHEMA = :db "
                        + "order by ORDINAL_POSITION")
                .bind("db", getSchemaDatabaseName(sourceName, database))
                .mapToMap(String.class)
                .forEach(
                    row -> {
                      String table = row.get("table_name");
                      tableColumnsMap.putIfAbsent(table, new LinkedList<>());
                      tableColumnsMap
                          .get(table)
                          .add(
                              new MysqlColumn(
                                  row.get("column_name"),
                                  row.get("data_type"),
                                  row.get("column_type"),
                                  "PRI".equals(row.get("column_key"))));
                    });
            return null;
          });
      return tableColumnsMap;
    } catch (Exception ex) {
      log.error(String.format("Failed to fetch table columns for database: %s", database), ex);
      throw new RuntimeException(ex);
    }
  }

  @VisibleForTesting
  String addSourcePrefix(@NotNull final String sql) {
    CharStream charStream = CharStreams.fromString(sql);
    MySQLLexer lexer = new MySQLLexer(charStream);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    MySQLParser parser = new MySQLParser(tokens);
    ParseTree tree = parser.root();
    ParseTreeWalker walker = new ParseTreeWalker();
    MySQLDBNamePrefixAdder prefixAdder =
        new com.airbnb.spinaltap.mysql.schema.MysqlSchemaDatabase.MySQLDBNamePrefixAdder(tokens);
    walker.walk(prefixAdder, tree);
    return prefixAdder.rewriter.getText();
  }

  private static String getSchemaDatabaseName(@NonNull final String source, final String database) {
    if (Strings.isNullOrEmpty(database)) {
      return null;
    }
    return String.format("%s%s%s", source, DELIMITER, database);
  }

  private class MySQLDBNamePrefixAdder extends MySQLBaseListener {
    final TokenStreamRewriter rewriter;

    MySQLDBNamePrefixAdder(TokenStream tokens) {
      rewriter = new TokenStreamRewriter(tokens);
    }

    @Override
    public void enterTable_name(MySQLParser.Table_nameContext ctx) {
      // If table name starts with dot(.), database name is not specified.
      // children.size() == 1 means no database name before table name
      if (!ctx.getText().startsWith(".") && ctx.children.size() != 1) {
        // The first child will be database name
        addPrefix(ctx.getChild(0).getText(), ctx.start);

        /*
        Add quotes around table name for a corner case:
        The database name is quoted but table name is not, and table name starts with a digit:
        Example:
        RENAME TABLE airbed3_production.20170810023312170_reservation2s to tmp.20170810023312170_reservation2s
         will be transformed to RENAME TABLE `source/airbed3_production`.20170810023312170_reservation2s to `source/tmp`.20170810023312170_reservation2s
         if we don't add quotes around table name, which is an invalid SQL statement in MySQL.
        */
        // DOT_ID will be null if there is already quotes around table name, _id(3) will be set in
        // this case.
        if (ctx.DOT_ID() != null) {
          rewriter.replace(ctx.stop, String.format(".`%s`", ctx.DOT_ID().getText().substring(1)));
        }
      }
    }

    @Override
    public void enterCreate_database(MySQLParser.Create_databaseContext ctx) {
      addPrefix(ctx.id_().getText(), ctx.id_().start);
    }

    @Override
    public void enterDrop_database(MySQLParser.Drop_databaseContext ctx) {
      addPrefix(ctx.id_().getText(), ctx.id_().start);
    }

    private void addPrefix(@NotNull final String name, @NotNull final Token indexToken) {
      if (!name.startsWith("`")) {
        rewriter.replace(indexToken, String.format("`%s%s%s`", sourceName, DELIMITER, name));
      } else {
        rewriter.replace(
            indexToken, String.format("`%s%s%s", sourceName, DELIMITER, name.substring(1)));
      }
    }
  }
}
