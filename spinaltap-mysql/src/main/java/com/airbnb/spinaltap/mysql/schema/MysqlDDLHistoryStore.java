/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.StringColumnMapper;

/** Represents a MySQL table to keep track of all processed DDL statements */
@Slf4j
@RequiredArgsConstructor
public class MysqlDDLHistoryStore {
  @NonNull private final String source;
  @NonNull private final DBI jdbi;
  @NonNull private final String archiveDatabase;
  @NonNull private final MysqlSourceMetrics metrics;

  private static final String PUT_DDL_HISTORY_QUERY =
      "INSERT INTO `%s` " + "(`binlog_position`, `DDL`, `created_at`) " + "VALUES (?, ?, ?)";
  private static final String GET_DDL_HISTORY_QUERY =
      "SELECT DDL FROM `%s` " + "WHERE `binlog_position` = :binlog_position";
  private static final String CREATE_DDL_HISTORY_STORE_TABLE_QUERY =
      "CREATE TABLE IF NOT EXISTS `%s` ("
          + "`id` int(11) unsigned NOT NULL AUTO_INCREMENT,"
          + "`binlog_position` varchar(255) NOT NULL,"
          + "`DDL` longtext NOT NULL,"
          + "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
          + "  PRIMARY KEY (`id`),"
          + "  UNIQUE KEY `binlog_position_index` (`binlog_position`)"
          + ") ENGINE=InnoDB DEFAULT CHARSET=utf8";
  private static final String ARCHIVE_DDL_HISTORY_STORE_TABLE_QUERY =
      "RENAME TABLE `%s` TO `%s`.`%s`";

  public void put(@NonNull BinlogFilePos binlogFilePos, @NonNull String ddl, long timestamp) {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.insert(
                String.format(PUT_DDL_HISTORY_QUERY, source),
                binlogFilePos.toString(),
                ddl,
                new Timestamp(timestamp));
            return null;
          });
      metrics.ddlHistoryStorePutSuccess();
    } catch (Exception ex) {
      log.error(
          String.format("Failed to put into DDL History store. source: %s. DDL: %s", source, ddl));
      metrics.ddlHistoryStorePutFailure(ex);
      throw new RuntimeException(ex);
    }
  }

  public String get(@NonNull BinlogFilePos binlogFilePos) {
    try (Handle handle = jdbi.open()) {
      String ddl =
          MysqlSchemaUtil.STRING_RETRYER.call(
              () ->
                  handle
                      .createQuery(String.format(GET_DDL_HISTORY_QUERY, source))
                      .bind("binlog_position", binlogFilePos.toString())
                      .map(StringColumnMapper.INSTANCE)
                      .first());
      metrics.ddlHistoryStoreGetSuccess();
      return ddl;
    } catch (Exception ex) {
      log.error(
          String.format("Failed to get DDL for binlog_position %s. Does it exist?", binlogFilePos),
          ex);
      metrics.ddlHistoryStoreGetFailure(ex);
      throw new RuntimeException(ex);
    }
  }

  public void create() {
    try (Handle handle = jdbi.open()) {
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(String.format(CREATE_DDL_HISTORY_STORE_TABLE_QUERY, source));
            return null;
          });
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to create DDL history store for source: %s. (Exception: %s)", source, ex));
      throw new RuntimeException(ex);
    }
  }

  public void archive() {
    try (Handle handle = jdbi.open()) {
      String archiveTableName =
          String.format(
              "%s_ddl_history_%s",
              source, new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
      MysqlSchemaUtil.VOID_RETRYER.call(
          () -> {
            handle.execute(
                String.format(
                    ARCHIVE_DDL_HISTORY_STORE_TABLE_QUERY,
                    source,
                    archiveDatabase,
                    archiveTableName));
            return null;
          });
      log.info("DDL history store for {} has been archived as {}", source, archiveTableName);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to archive DDL history store for source: %s. (Exception: %s)", source, ex));
      throw new RuntimeException(ex);
    }
  }
}
