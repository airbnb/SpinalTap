/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.GtidSet;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.io.IOException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.StatementContext;

@Slf4j
@RequiredArgsConstructor
public class MysqlSchemaStore {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String CREATE_SCHEMA_STORE_TABLE_QUERY =
      "CREATE TABLE IF NOT EXISTS `%s`.`%s` ("
          + "`id` bigint(20) NOT NULL AUTO_INCREMENT,"
          + "`database` varchar(255),"
          + "`table` varchar(255),"
          + "`binlog_file_position` varchar(255) NOT NULL,"
          + "`server_uuid` varchar(255),"
          + "`gtid_set` text,"
          + "`gtid` varchar(255),"
          + "`columns` text,"
          + "`sql` text,"
          + "`meta_data` text DEFAULT NULL,"
          + "`timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
          + "  PRIMARY KEY (`id`),"
          + "  KEY `binlog_file_position_index` (`binlog_file_position`),"
          + "  KEY `gtid_index` (`gtid`)"
          + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin";
  private static final String PUT_SCHEMA_QUERY =
      "INSERT INTO `%s`.`%s`"
          + " (`database`, `table`, `binlog_file_position`, `server_uuid`, `gtid_set`, `gtid`, `columns`, `sql`, `meta_data`, `timestamp`)"
          + " VALUES (:database, :table, :binlog_file_position, :server_uuid, :gtid_set, :gtid, :columns, :sql, :meta_data, :timestamp)";
  private final String sourceName;
  private final String storeDBName;
  private final String archiveDBName;
  private final Jdbi jdbi;
  private final MysqlSourceMetrics metrics;
  // Schema cache should always reflect the schema we currently need
  @Getter
  private final Table<String, String, MysqlTableSchema> schemaCache =
      Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);

  public boolean isCreated() {
    return jdbi.withHandle(
            handle ->
                handle
                    .createQuery(
                        "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema = :db AND table_name = :table")
                    .bind("db", storeDBName)
                    .bind("table", sourceName)
                    .mapTo(String.class)
                    .findFirst())
        .isPresent();
  }

  public void loadSchemaCacheUntil(BinlogFilePos pos) {
    schemaCache.clear();
    for (MysqlTableSchema schema : getAllSchemas()) {
      if (schema.getBinlogFilePos().compareTo(pos) > 0) {
        break;
      }
      updateSchemaCache(schema);
    }
  }

  @VisibleForTesting
  List<MysqlTableSchema> getAllSchemas() {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    String.format(
                        "SELECT * FROM `%s`.`%s` ORDER BY id ASC", storeDBName, sourceName))
                .map(MysqlTableSchemaMapper.INSTANCE)
                .list());
  }

  public MysqlTableSchema get(String database, String table) {
    if (schemaCache.contains(database, table)) {
      metrics.schemaStoreGetSuccess(database, table);
      return schemaCache.get(database, table);
    } else {
      RuntimeException ex =
          new RuntimeException(
              String.format("No schema found for database: %s table: %s", database, table));
      metrics.schemaStoreGetFailure(database, table, ex);
      throw ex;
    }
  }

  public void put(MysqlTableSchema schema) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            GtidSet gtidSet = schema.getBinlogFilePos().getGtidSet();
            long id =
                handle
                    .createUpdate(String.format(PUT_SCHEMA_QUERY, storeDBName, sourceName))
                    .bind("database", schema.getDatabase())
                    .bind("table", schema.getTable())
                    .bind("binlog_file_position", schema.getBinlogFilePos().toString())
                    .bind("server_uuid", schema.getBinlogFilePos().getServerUUID())
                    .bind("gtid_set", gtidSet == null ? null : gtidSet.toString())
                    .bind("gtid", schema.getGtid())
                    .bind("columns", OBJECT_MAPPER.writeValueAsString(schema.getColumns()))
                    .bind("sql", schema.getSql())
                    .bind("meta_data", OBJECT_MAPPER.writeValueAsString(schema.getMetadata()))
                    .bind("timestamp", new Timestamp(schema.getTimestamp()))
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            // MysqlTableSchema is immutable so we have to create a new one and update cache
            updateSchemaCache(
                new MysqlTableSchema(
                    id,
                    schema.getDatabase(),
                    schema.getTable(),
                    schema.getBinlogFilePos(),
                    schema.getGtid(),
                    schema.getSql(),
                    schema.getTimestamp(),
                    schema.getColumns(),
                    schema.getMetadata()));
            metrics.schemaStorePutSuccess(schema.getDatabase(), schema.getTable());
            return null;
          });
    } catch (Exception ex) {
      log.error("Failed to put table schema {}. Exception: {}", schema, ex.toString());
      metrics.schemaStorePutFailure(schema.getDatabase(), schema.getTable(), ex);
      throw new RuntimeException(ex);
    }
  }

  public void bootstrap(List<MysqlTableSchema> schemas) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(String.format(CREATE_SCHEMA_STORE_TABLE_QUERY, storeDBName, sourceName));
            PreparedBatch batch =
                handle.prepareBatch(String.format(PUT_SCHEMA_QUERY, storeDBName, sourceName));
            for (MysqlTableSchema schema : schemas) {
              GtidSet gtidSet = schema.getBinlogFilePos().getGtidSet();
              batch
                  .bind("database", schema.getDatabase())
                  .bind("table", schema.getTable())
                  .bind("binlog_file_position", schema.getBinlogFilePos().toString())
                  .bind("server_uuid", schema.getBinlogFilePos().getServerUUID())
                  .bind("gtid_set", gtidSet == null ? null : gtidSet.toString())
                  .bind("gtid", schema.getGtid())
                  .bind("columns", OBJECT_MAPPER.writeValueAsString(schema.getColumns()))
                  .bind("sql", schema.getSql())
                  .bind("meta_data", OBJECT_MAPPER.writeValueAsString(schema.getMetadata()))
                  .bind("timestamp", new Date(schema.getTimestamp()))
                  .add();
            }
            batch.execute();
            getAllSchemas().forEach(this::updateSchemaCache);
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format("Failed to bootstrap schema store for %s. exception: %s", sourceName, ex));
      throw new RuntimeException(ex);
    }
  }

  public List<MysqlTableSchema> queryByBinlogFilePos(BinlogFilePos pos) {
    try (Handle handle = jdbi.open()) {
      return MysqlSchemaUtil.LIST_TABLE_SCHEMA_RETRYER.call(
          () ->
              handle
                  .createQuery(
                      String.format(
                          "SELECT * FROM `%s`.`%s` WHERE binlog_file_position = :pos",
                          storeDBName, sourceName))
                  .bind("pos", pos)
                  .map(MysqlTableSchemaMapper.INSTANCE)
                  .list());
    } catch (Exception ex) {
      log.error(
          String.format("Failed to query table schema by binlog pos: %s. Exception: %s", pos, ex));
      throw new RuntimeException(ex);
    }
  }

  public List<MysqlTableSchema> queryByGTID(String gtid) {
    try (Handle handle = jdbi.open()) {
      return handle
          .createQuery(
              String.format("SELECT * FROM `%s`.`%s` WHERE gtid = :gtid", storeDBName, sourceName))
          .bind("gtid", gtid)
          .map(MysqlTableSchemaMapper.INSTANCE)
          .list();
    } catch (Exception ex) {
      log.error(String.format("Failed to query table schema by GTID: %s. Exception: %s", gtid, ex));
      throw new RuntimeException(ex);
    }
  }

  public void archive() {
    String archiveTableName =
        String.format(
            "%s_%s",
            sourceName, new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date()));
    jdbi.useHandle(
        handle ->
            handle.execute(
                String.format(
                    "RENAME TABLE `%s`.`%s` TO `%s`.`%s`",
                    storeDBName, sourceName, archiveDBName, archiveTableName)));
    schemaCache.clear();
  }

  public void compress(BinlogFilePos earliestPos) {
    deleteSchemas(getRowIdsToDelete(earliestPos));
  }

  @VisibleForTesting
  Set<Long> getRowIdsToDelete(BinlogFilePos earliestPos) {
    Table<String, String, List<MysqlTableSchema>> allSchemas =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    Set<Long> rowIdsToDelete = new HashSet<>();
    getAllSchemas()
        .forEach(
            schema -> {
              String database = schema.getDatabase();
              String table = schema.getTable();
              if (database == null || table == null) {
                if (schema.getBinlogFilePos().compareTo(earliestPos) < 0) {
                  rowIdsToDelete.add(schema.getId());
                }
              } else {
                if (!allSchemas.contains(database, table)) {
                  allSchemas.put(database, table, new LinkedList<>());
                }
                allSchemas.get(database, table).add(schema);
              }
            });

    for (List<MysqlTableSchema> schemas : allSchemas.values()) {
      for (MysqlTableSchema schema : schemas) {
        if (schema.getBinlogFilePos().compareTo(earliestPos) >= 0) {
          break;
        }
        if (!schema.equals(schemaCache.get(schema.getDatabase(), schema.getTable()))) {
          rowIdsToDelete.add(schema.getId());
        }
      }
    }
    return rowIdsToDelete;
  }

  private void deleteSchemas(Collection<Long> ids) {
    log.info("Deleting {} rows from schema store. IDS: {}", ids.size(), ids);
    jdbi.useHandle(
        handle ->
            handle
                .createUpdate(
                    String.format(
                        "DELETE FROM `%s`.`%s` WHERE id IN (<rowIdsToDelete>)",
                        storeDBName, sourceName))
                .bindList("rowIdsToDelete", ids)
                .execute());
  }

  void updateSchemaCache(MysqlTableSchema schema) {
    String database = schema.getDatabase();
    String table = schema.getTable();
    if (database == null || table == null) {
      return;
    }
    if (!schema.getColumns().isEmpty()) {
      schemaCache.put(database, table, schema);
    } else if (schemaCache.contains(database, table)) {
      schemaCache.remove(database, table);
    }
  }

  private static class MysqlTableSchemaMapper implements RowMapper<MysqlTableSchema> {
    public static MysqlTableSchemaMapper INSTANCE = new MysqlTableSchemaMapper();

    @Override
    public MysqlTableSchema map(ResultSet rs, StatementContext ctx) throws SQLException {
      BinlogFilePos pos = BinlogFilePos.fromString(rs.getString("binlog_file_position"));
      pos.setServerUUID(rs.getString("server_uuid"));
      String gtidSet = rs.getString("gtid_set");
      if (gtidSet != null) {
        pos.setGtidSet(new GtidSet(gtidSet));
      }
      List<MysqlColumn> columns = Collections.emptyList();
      Map<String, String> metadata = Collections.emptyMap();
      String columnsStr = rs.getString("columns");
      if (columnsStr != null) {
        try {
          columns = OBJECT_MAPPER.readValue(columnsStr, new TypeReference<List<MysqlColumn>>() {});
        } catch (IOException ex) {
          log.error(
              String.format("Failed to deserialize columns %s. exception: %s", columnsStr, ex));
        }
      }

      String metadataStr = rs.getString("meta_data");
      if (metadataStr != null) {
        try {
          metadata =
              OBJECT_MAPPER.readValue(metadataStr, new TypeReference<Map<String, String>>() {});
        } catch (IOException ex) {
          log.error(
              String.format("Failed to deserialize metadata %s. exception: %s", metadataStr, ex));
          throw new RuntimeException(ex);
        }
      }

      return new MysqlTableSchema(
          rs.getLong("id"),
          rs.getString("database"),
          rs.getString("table"),
          pos,
          rs.getString("gtid"),
          rs.getString("sql"),
          rs.getTimestamp("timestamp").getTime(),
          columns,
          metadata);
    }
  }
}
