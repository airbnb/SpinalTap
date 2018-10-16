/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.IntegerColumnMapper;
import org.skife.jdbi.v2.util.StringColumnMapper;

/**
 * Represents an implementation of {@link com.airbnb.spinaltap.mysql.schema.SchemaStore} which
 * stores the schema history in MySQL table
 */
@Slf4j
public class MysqlSchemaStore extends AbstractMysqlSchemaStore
    implements SchemaStore<MysqlTableSchema> {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new GuavaModule());
  private static final String CHECK_TABLE_EXISTS_QUERY = "SHOW TABLES LIKE '%s'";
  private static final String PUT_SCHEMA_QUERY =
      "INSERT INTO `%s` "
          + "(`version`, `source`, `database`, `table`, `binlog_filename`, `binlog_position`, `schema_info`) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";
  private static final String GET_SCHEMA_BY_VERSION_QUERY =
      "SELECT `schema_info` FROM `%s` "
          + "WHERE `database` = :database AND `table` = :table AND `version` = :version";
  private static final String GET_SCHEMA_BY_BINLOG_FILE_POSITION_QUERY =
      "SELECT `schema_info` FROM `%s` "
          + "WHERE `binlog_filename` = :binlog_filename AND `binlog_position` = :binlog_position";
  private static final String GET_ALL_SCHEMA_BY_TABLE_QUERY =
      "SELECT `schema_info` FROM `%s` " + "WHERE `database` = :database AND `table` = :table";
  private static final String GET_LATEST_SCHEMA_QUERY =
      "SELECT `schema_info` FROM `%s` "
          + "WHERE `database` = :database AND `table` = :table ORDER BY `version` DESC LIMIT 1";
  private static final String GET_ALL_SCHEMA_QUERY =
      "SELECT `schema_info` FROM `%s` ORDER BY `version` ASC";
  private static final String GET_LATEST_VERSION_QUERY =
      "SELECT MAX(version) AS latest_version FROM `%s` "
          + "WHERE `database` = :database AND `table` = :table";
  private static final String TABLE_EXISTS_QUERY =
      "SELECT 1 FROM `%s` " + "WHERE `database` = :database AND `table` = :table LIMIT 1";
  private static final String CREATE_SCHEMA_STORE_TABLE_QUERY =
      "CREATE TABLE IF NOT EXISTS `%s` ("
          + "`id` int(11) unsigned NOT NULL AUTO_INCREMENT,"
          + "`version` int(10) unsigned NOT NULL,"
          + "`source` varchar(255) NOT NULL DEFAULT '',"
          + "`database` varchar(255) NOT NULL DEFAULT '',"
          + "`table` varchar(255) NOT NULL DEFAULT '',"
          + "`binlog_filename` varchar(255) NOT NULL,"
          + "`binlog_position` bigint(20) NOT NULL,"
          + "`schema_info` longtext NOT NULL,"
          + "`meta_data` longblob DEFAULT NULL,"
          + "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
          + "  PRIMARY KEY (`id`),"
          + "  KEY `version_index` (`version`),"
          + "  UNIQUE KEY `binlog_position_index` (`source`,`database`,`table`,`binlog_filename`,`binlog_position`),"
          + "  UNIQUE KEY `version_query_index` (`source`,`database`,`table`,`version`)"
          + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin";
  private static final String ARCHIVE_SCHEMA_STORE_TABLE_QUERY = "RENAME TABLE `%s` TO `%s`.`%s`";
  private static final String CREATE_ARCHIVE_TABLE_QUERY = "CREATE TABLE `%s`.`%s` LIKE `%s`";
  private static final String INSERT_ARCHIVE_TABLE_QUERY =
      "INSERT INTO `%s`.`%s` SELECT * FROM `%s` WHERE `database` = '%s'";
  private static final String DELETE_DATABASE_FROM_SCHEMA_STORE_TABLE_QUERY =
      "DELETE FROM `%s` WHERE `database` = '%s'";

  private final DBI jdbi;
  private final String archiveDatabase;

  public MysqlSchemaStore(
      @NotNull final String source,
      @NotNull final DBI jdbi,
      @NotNull final String archiveDatabase,
      @NotNull final MysqlSourceMetrics metrics) {
    super(source, metrics);
    this.jdbi = jdbi;
    this.archiveDatabase = archiveDatabase;
  }

  @Override
  public void put(@NotNull final MysqlTableSchema schema) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.insert(
                String.format(PUT_SCHEMA_QUERY, source),
                schema.getVersion(),
                schema.getSource(),
                schema.getDatabase(),
                schema.getTable(),
                schema.getBinlogFilePos().getFileName(),
                schema.getBinlogFilePos().getPosition(),
                OBJECT_MAPPER.writeValueAsString(schema));
            return null;
          });
      metrics.schemaStorePutSuccess(schema.getDatabase(), schema.getTable());
    } catch (Exception ex) {
      log.error("Failed to put schema {}.", schema);
      metrics.schemaStorePutFailure(schema.getDatabase(), schema.getTable(), ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public MysqlTableSchema query(
      @NotNull final String database,
      @NotNull final String table,
      @NotNull final BinlogFilePos binlogFilePos) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MysqlTableSchema get(
      @NotNull final String database, @NotNull final String table, final int version) {
    try (Handle handle = jdbi.open()) {
      String schemaInfo =
          MysqlSchemaUtil.STRING_RETRYER.call(
              () ->
                  handle
                      .createQuery(String.format(GET_SCHEMA_BY_VERSION_QUERY, source))
                      .bind("database", database)
                      .bind("table", table)
                      .bind("version", version)
                      .map(StringColumnMapper.INSTANCE)
                      .first());
      metrics.schemaStoreGetSuccess(database, table);
      return deserializeSchemaInfo(schemaInfo);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to get schema of database: %s table: %s version: %d. Does it exist?",
              database, table, version),
          ex);
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public MysqlTableSchema get(@NotNull final BinlogFilePos binlogFilePos) {
    try (Handle handle = jdbi.open()) {
      String schemaInfo =
          MysqlSchemaUtil.STRING_RETRYER.call(
              () ->
                  handle
                      .createQuery(GET_SCHEMA_BY_BINLOG_FILE_POSITION_QUERY)
                      .bind("binlog_filename", binlogFilePos.getFileName())
                      .bind("binlog_position", binlogFilePos.getPosition())
                      .map(StringColumnMapper.INSTANCE)
                      .first());
      return schemaInfo != null ? deserializeSchemaInfo(schemaInfo) : null;
    } catch (Exception ex) {
      log.error(
          String.format("Failed to get schema change at binlog position: %s", binlogFilePos), ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public MysqlTableSchema getLatest(@NotNull final String database, @NotNull final String table) {
    try (Handle handle = jdbi.open()) {
      String schemaInfo =
          MysqlSchemaUtil.STRING_RETRYER.call(
              () ->
                  handle
                      .createQuery(String.format(GET_LATEST_SCHEMA_QUERY, source))
                      .bind("database", database)
                      .bind("table", table)
                      .map(StringColumnMapper.INSTANCE)
                      .first());
      metrics.schemaStoreGetSuccess(database, table);
      return deserializeSchemaInfo(schemaInfo);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to get latest schema of database: %s table: %s. Does it exist?",
              database, table),
          ex);
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Map<String, MysqlTableSchema> getLatest(@NotNull final String database) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLatestVersion(@NotNull final String database, @NotNull final String table) {
    try (Handle handle = jdbi.open()) {
      metrics.schemaStoreGetSuccess(database, table);
      return MysqlSchemaUtil.INTEGER_RETRYER.call(
          () ->
              handle
                  .createQuery(String.format(GET_LATEST_VERSION_QUERY, source))
                  .bind("database", database)
                  .bind("table", table)
                  .map(IntegerColumnMapper.WRAPPER)
                  .first());
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to get latest schema version of database: %s table: %s. Does it exist?",
              database, table),
          ex);
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Table<String, String, TreeMap<Integer, MysqlTableSchema>> getAll() {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allSchemaTable =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    List<String> allSchemaInfo;

    try (Handle handle = jdbi.open()) {
      allSchemaInfo =
          MysqlSchemaUtil.LIST_STRING_RETRYER.call(
              () ->
                  handle
                      .createQuery(String.format(GET_ALL_SCHEMA_QUERY, source))
                      .map(StringColumnMapper.INSTANCE)
                      .list());
    } catch (Exception ex) {
      log.error(String.format("Failed to get all schema for source: %s", source), ex);
      throw new RuntimeException(ex);
    }

    allSchemaInfo
        .stream()
        .map(MysqlSchemaStore::deserializeSchemaInfo)
        .forEach(
            schemaInfo -> {
              String database = schemaInfo.getDatabase();
              String table = schemaInfo.getTable();
              int version = schemaInfo.getVersion();
              if (!allSchemaTable.contains(database, table)) {
                allSchemaTable.put(database, table, Maps.newTreeMap());
              }
              allSchemaTable.get(database, table).put(version, schemaInfo);
            });

    return allSchemaTable;
  }

  @Override
  public TreeMap<Integer, MysqlTableSchema> getAll(
      @NotNull final String database, @NotNull final String table) {
    TreeMap<Integer, MysqlTableSchema> allSchemaVersions = Maps.newTreeMap();
    List<String> allSchemaInfo;

    try (Handle handle = jdbi.open()) {
      allSchemaInfo =
          MysqlSchemaUtil.LIST_STRING_RETRYER.call(
              () ->
                  handle
                      .createQuery(String.format(GET_ALL_SCHEMA_BY_TABLE_QUERY, source))
                      .map(StringColumnMapper.INSTANCE)
                      .list());
      metrics.schemaStoreGetSuccess(database, table);
    } catch (Exception ex) {
      log.error(
          String.format("Failed to get all schema for database: %s table: %s", database, table),
          ex);
      metrics.schemaStoreGetFailure(database, table, ex);
      throw new RuntimeException(ex);
    }

    allSchemaInfo
        .stream()
        .map(MysqlSchemaStore::deserializeSchemaInfo)
        .forEach(schemaInfo -> allSchemaVersions.put(schemaInfo.getVersion(), schemaInfo));

    return allSchemaVersions;
  }

  @Override
  public boolean exists(@NotNull final String database, @NotNull final String table) {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.BOOLEAN_RETRYER.call(
          () ->
              null
                  != handle
                      .createQuery(String.format(TABLE_EXISTS_QUERY, source))
                      .bind("database", database)
                      .bind("table", table)
                      .map(StringColumnMapper.INSTANCE)
                      .first());
    } catch (Exception ex) {
      log.error(String.format("Failed to check if %s:%s exists in schema store.", database, table));
      throw new RuntimeException(ex);
    }
  }

  public void create() {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(String.format(CREATE_SCHEMA_STORE_TABLE_QUERY, source));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to create schema store for source: %s. (Exception: %s)", source, ex));
      throw new RuntimeException(ex);
    }
  }

  public boolean isCreated() {
    try (Handle handle = jdbi.open()) {
      return !MysqlSchemaUtil.LIST_STRING_RETRYER
          .call(
              () ->
                  handle
                      .createQuery(String.format(CHECK_TABLE_EXISTS_QUERY, source))
                      .map(StringColumnMapper.INSTANCE)
                      .list())
          .isEmpty();
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to check if schema store table exists for source %s (Exception: %s)",
              source, ex));
      throw new RuntimeException(ex);
    }
  }

  public void archive() {
    try (Handle handle = jdbi.open()) {
      String archiveTableName =
          String.format("%s_%s", source, new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(
                String.format(
                    ARCHIVE_SCHEMA_STORE_TABLE_QUERY, source, archiveDatabase, archiveTableName));
            return null;
          });
      log.info("Schema store for {} has been archived as {}", source, archiveTableName);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to archive schema store for source: %s. (Exception: %s)", source, ex));
      throw new RuntimeException(ex);
    }
  }

  void archive(@NotNull final String database) {
    String archiveTableName =
        String.format(
            "%s_%s_%s",
            source, database, new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
    createArchiveTable(database, archiveTableName);
    archiveRows(database, archiveTableName);
    deleteRows(database);
  }

  private void createArchiveTable(
      @NotNull final String database, @NotNull final String archiveTableName) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(
                String.format(
                    CREATE_ARCHIVE_TABLE_QUERY, archiveDatabase, archiveTableName, source));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to create archive table for source: %s database: %s (Exception: %s)",
              source, database, ex));
      throw new RuntimeException(ex);
    }
  }

  private void archiveRows(@NotNull final String database, @NotNull final String archiveTableName) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(
                String.format(
                    INSERT_ARCHIVE_TABLE_QUERY,
                    archiveDatabase,
                    archiveTableName,
                    source,
                    database));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to insert into archive table for source: %s database: %s (Exception: %s)",
              source, database, ex));
      throw new RuntimeException(ex);
    }
  }

  private void deleteRows(@NotNull final String database) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(
                String.format(DELETE_DATABASE_FROM_SCHEMA_STORE_TABLE_QUERY, source, database));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to delete schema store rows for source: %s database: %s (Exception: %s)",
              source, database, ex));
      throw new RuntimeException(ex);
    }
  }

  private static MysqlTableSchema deserializeSchemaInfo(String schemaInfo) {
    try {
      return OBJECT_MAPPER.readValue(schemaInfo, MysqlTableSchema.class);
    } catch (Exception ex) {
      log.error("Failed to deserialize schema json string.");
      throw new RuntimeException(ex);
    }
  }
}
