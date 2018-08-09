/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.skife.jdbi.v2.DBI;

/**
 * The factory class of {@link com.airbnb.spinaltap.mysql.schema.MysqlSchemaStoreManager} which
 * provides necessary initialization and setups.
 */
@RequiredArgsConstructor
public class MysqlSchemaStoreManagerFactory {
  @NonNull private final String mysqlUser;
  @NonNull private final String mysqlPassword;
  @NonNull private final MysqlSchemaStoreConfiguration schemaStoreConfiguration;

  public MysqlSchemaStoreManager create(
      @NonNull final String source,
      @NonNull final MysqlConfiguration mysqlConfiguration,
      @NonNull final MysqlSourceMetrics metrics) {
    final DBI schemaReaderDBI =
        MysqlSchemaUtil.createMysqlDBI(
            mysqlConfiguration.getHost(),
            mysqlConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            null);

    final DBI schemaStoreDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            schemaStoreConfiguration.getDatabase());

    final DBI schemaDatabaseDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            null);

    final DBI ddlHistoryStoreDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            schemaStoreConfiguration.getDdlHistoryStoreDatabase());

    return new MysqlSchemaStoreManager(
        source,
        new LatestMysqlSchemaStore(source, schemaReaderDBI, metrics),
        new MysqlSchemaStore(
            source, schemaStoreDBI, schemaStoreConfiguration.getArchiveDatabase(), metrics),
        new MysqlSchemaDatabase(source, schemaDatabaseDBI, metrics),
        new MysqlDDLHistoryStore(
            source, ddlHistoryStoreDBI, schemaStoreConfiguration.getArchiveDatabase(), metrics));
  }

  public SchemaStoreArchiver createArchiver(
      @NonNull final String source, @NonNull final MysqlSourceMetrics metrics) {
    final DBI schemaStoreDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            schemaStoreConfiguration.getDatabase());

    final DBI schemaDatabaseDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            null);

    final DBI ddlHistoryStoreDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            schemaStoreConfiguration.getDdlHistoryStoreDatabase());

    return new MysqlSchemaStoreManager(
        source,
        null,
        new MysqlSchemaStore(
            source, schemaStoreDBI, schemaStoreConfiguration.getArchiveDatabase(), metrics),
        new MysqlSchemaDatabase(source, schemaDatabaseDBI, metrics),
        new MysqlDDLHistoryStore(
            source, ddlHistoryStoreDBI, schemaStoreConfiguration.getArchiveDatabase(), metrics));
  }
}
