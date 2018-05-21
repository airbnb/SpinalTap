/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.StringColumnMapper;

@Slf4j
public class MysqlSchemaDatabase {
  private static final char DELIMITER = '/';
  private static final String CREATE_DATABASE_QUERY = "CREATE DATABASE `%s`";
  private static final String LIST_DATABASES_QUERY =
      "select SCHEMA_NAME from information_schema.SCHEMATA " + "where SCHEMA_NAME LIKE '%s%s%%'";
  private static final String DROP_DATABASES_QUERY = "DROP DATABASE IF EXISTS `%s`";
  private static final String TABLE_SCHEMA_QUERY =
      "select TABLE_NAME, COLUMN_NAME, COLUMN_KEY, COLUMN_TYPE from information_schema.COLUMNS "
          + "where TABLE_SCHEMA = :db and TABLE_NAME = :table "
          + "order by ORDINAL_POSITION";
  private static final String ALL_TABLE_SCHEMA_QUERY =
      "select TABLE_NAME, COLUMN_NAME, COLUMN_KEY, COLUMN_TYPE from information_schema.COLUMNS "
          + "where TABLE_SCHEMA = :db "
          + "order by ORDINAL_POSITION";

  private final String source;
  private final DBI jdbi;
  private final MysqlSourceMetrics metrics;

  public MysqlSchemaDatabase(
      @NotNull final String source,
      @NotNull final DBI jdbi,
      @NotNull final MysqlSourceMetrics metrics) {
    this.source = source;
    this.jdbi = jdbi;
    this.metrics = metrics;
  }

  void applyDDLStatement(@NotNull final String database, @NotNull final String ddl) {
    log.info(String.format("Applying DDL statement: %s (Database selected: %s)", ddl, database));
    try (Handle handle = jdbi.open()) {
      disableForeignKeyChecks(handle);
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            MysqlSchemaUtil.executeSQL(
                handle,
                database.isEmpty() ? null : getSchemaDatabaseName(source, database),
                addSourcePrefix(ddl));
            return null;
          });
      metrics.schemaDatabaseApplyDDLSuccess(database);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to apply DDL Statement to source: %s database: %s. (SQL: %s. Exception: %s)",
              source, database, ddl, ex));
      metrics.schemaDatabaseApplyDDLFailure(database, ex);
      throw new RuntimeException(ex);
    }
  }

  void createDatabase(@NotNull final String database) {
    log.info("Creating database: {}", database);
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            MysqlSchemaUtil.executeSQL(
                handle,
                null,
                String.format(
                    CREATE_DATABASE_QUERY,
                    getSchemaDatabaseName(source, MysqlSchemaUtil.escapeBackQuote(database))));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to create database %s (Exception: %s)",
              getSchemaDatabaseName(source, database), ex));
      throw new RuntimeException(ex);
    }
  }

  public void dropDatabases() {
    listDatabases().forEach(this::dropDatabase);
  }

  void dropDatabase(@NotNull final String database) {
    log.info("Dropping database: {}", database);
    try (Handle handle = jdbi.open()) {
      disableForeignKeyChecks(handle);
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            MysqlSchemaUtil.executeSQL(
                handle,
                null,
                String.format(
                    DROP_DATABASES_QUERY,
                    MysqlSchemaUtil.escapeBackQuote(getSchemaDatabaseName(source, database))));
            return null;
          });
    } catch (Exception ex) {
      log.error(String.format("Failed to drop database %s. (Exception: %s)", database, ex));
      throw new RuntimeException(ex);
    }
  }

  Map<String, MysqlTableSchema> fetchTableSchema(@NotNull final String database) {
    List<ColumnInfo> allColumnInfo;
    try (Handle handle = jdbi.open()) {
      disableForeignKeyChecks(handle);
      allColumnInfo =
          MysqlSchemaUtil.LIST_COLUMNINFO_RETRYER.call(
              () ->
                  handle
                      .createQuery(ALL_TABLE_SCHEMA_QUERY)
                      .bind("db", getSchemaDatabaseName(source, database))
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
                          source, database, table, "", Lists.newArrayList()))
              .getColumnInfo()
              .add(columnInfo);
        });

    return allTableSchemaMap;
  }

  MysqlTableSchema fetchTableSchema(@NotNull final String database, @NotNull final String table) {
    try (Handle handle = jdbi.open()) {
      List<ColumnInfo> columnInfoList =
          MysqlSchemaUtil.LIST_COLUMNINFO_RETRYER.call(
              () ->
                  handle
                      .createQuery(TABLE_SCHEMA_QUERY)
                      .bind("db", database)
                      .bind("table", table)
                      .map(MysqlSchemaUtil.COLUMN_MAPPER)
                      .list());
      return MysqlSchemaUtil.createTableSchema(source, database, table, "", columnInfoList);
    } catch (Exception ex) {
      log.error(String.format("Failed to fetch schema for table %s, db %s", table, database), ex);
      throw new RuntimeException(ex);
    }
  }

  Set<String> listDatabases() {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_STRING_RETRYER
          .call(
              () ->
                  handle
                      .createQuery(String.format(LIST_DATABASES_QUERY, source, DELIMITER))
                      .map(StringColumnMapper.INSTANCE)
                      .list())
          .stream()
          .map(database -> database.replaceFirst(String.format("^%s%s", source, DELIMITER), ""))
          .collect(Collectors.toSet());
    } catch (Exception ex) {
      log.error(
          String.format("Failed to list databases for source: %s (Exception: %s)", source, ex));
      throw new RuntimeException(ex);
    }
  }

  private void disableForeignKeyChecks(@NotNull final Handle handle) {
    handle.execute("SET foreign_key_checks=0");
  }

  @VisibleForTesting
  String addSourcePrefix(@NotNull final String ddl) {
    CharStream charStream = CharStreams.fromString(ddl);
    MySQLLexer lexer = new MySQLLexer(charStream);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    MySQLParser parser = new MySQLParser(tokens);
    ParseTree tree = parser.root();
    ParseTreeWalker walker = new ParseTreeWalker();
    MySQLDBNamePrefixAdder prefixAdder = new MySQLDBNamePrefixAdder(tokens);
    walker.walk(prefixAdder, tree);
    return prefixAdder.rewriter.getText();
  }

  private static String getSchemaDatabaseName(
      @NotNull final String source, @NotNull final String database) {
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
        rewriter.replace(indexToken, String.format("`%s%s%s`", source, DELIMITER, name));
      } else {
        rewriter.replace(
            indexToken, String.format("`%s%s%s", source, DELIMITER, name.substring(1)));
      }
    }
  }
}
