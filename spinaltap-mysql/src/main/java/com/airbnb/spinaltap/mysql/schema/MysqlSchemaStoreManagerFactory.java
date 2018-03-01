/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.skife.jdbi.v2.DBI;

@RequiredArgsConstructor
public class MysqlSchemaStoreManagerFactory {
  private final String mysqlUser;
  private final String mysqlPassword;
  private final MysqlSchemaStoreConfiguration schemaStoreConfiguration;

  public MysqlSchemaStoreManager create(
      @NotNull final String source,
      @NotNull final MysqlConfiguration mysqlConfiguration,
      @NotNull final MysqlSourceMetrics metrics) {
    DBI schemaReaderDBI =
        MysqlSchemaUtil.createMysqlDBI(
            mysqlConfiguration.getHost(),
            mysqlConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            null);
    DBI schemaStoreDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            schemaStoreConfiguration.getDatabase());
    DBI schemaDatabaseDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            null);

    return new MysqlSchemaStoreManager(
        source,
        new LatestMysqlSchemaStore(source, schemaReaderDBI, metrics),
        new MysqlSchemaStore(
            source, schemaStoreDBI, schemaStoreConfiguration.getArchiveDatabase(), metrics),
        new MysqlSchemaDatabase(source, schemaDatabaseDBI, metrics));
  }

  public SchemaStoreArchiver createArchiver(
      @NotNull final String source, @NotNull final MysqlSourceMetrics metrics) {
    DBI schemaStoreDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            schemaStoreConfiguration.getDatabase());
    DBI schemaDatabaseDBI =
        MysqlSchemaUtil.createMysqlDBI(
            schemaStoreConfiguration.getHost(),
            schemaStoreConfiguration.getPort(),
            mysqlUser,
            mysqlPassword,
            null);

    return new MysqlSchemaStoreManager(
        source,
        null,
        new MysqlSchemaStore(
            source, schemaStoreDBI, schemaStoreConfiguration.getArchiveDatabase(), metrics),
        new MysqlSchemaDatabase(source, schemaDatabaseDBI, metrics));
  }
}
