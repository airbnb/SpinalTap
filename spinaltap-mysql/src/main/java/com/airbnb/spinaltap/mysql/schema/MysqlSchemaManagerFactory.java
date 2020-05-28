/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.mysql.MysqlClient;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import org.jdbi.v3.core.Jdbi;

public class MysqlSchemaManagerFactory {
  private final String username;
  private final String password;
  private final MysqlSchemaStoreConfiguration configuration;
  private Jdbi jdbi;

  public MysqlSchemaManagerFactory(
      final String username,
      final String password,
      final MysqlSchemaStoreConfiguration configuration) {
    this.username = username;
    this.password = password;
    this.configuration = configuration;

    if (configuration != null) {
      jdbi =
          Jdbi.create(
              MysqlClient.createMysqlDataSource(
                  configuration.getHost(), configuration.getPort(), username, password));
      jdbi.useHandle(
          handle -> {
            handle.execute(
                String.format("CREATE DATABASE IF NOT EXISTS `%s`", configuration.getDatabase()));
            handle.execute(
                String.format(
                    "CREATE DATABASE IF NOT EXISTS `%s`", configuration.getArchiveDatabase()));
          });
    }
  }

  public MysqlSchemaManager create(
      String sourceName,
      MysqlClient mysqlClient,
      boolean isSchemaVersionEnabled,
      MysqlSourceMetrics metrics) {
    MysqlSchemaReader schemaReader =
        new MysqlSchemaReader(sourceName, mysqlClient.getJdbi(), metrics);

    if (!isSchemaVersionEnabled) {
      return new MysqlSchemaManager(sourceName, null, null, schemaReader, mysqlClient, false);
    }

    MysqlSchemaStore schemaStore =
        new MysqlSchemaStore(
            sourceName,
            configuration.getDatabase(),
            configuration.getArchiveDatabase(),
            jdbi,
            metrics);
    MysqlSchemaDatabase schemaDatabase = new MysqlSchemaDatabase(sourceName, jdbi, metrics);
    return new MysqlSchemaManager(
        sourceName, schemaStore, schemaDatabase, schemaReader, mysqlClient, true);
  }

  public MysqlSchemaArchiver createArchiver(String sourceName) {
    MysqlSourceMetrics metrics = new MysqlSourceMetrics(sourceName, new TaggedMetricRegistry());
    Jdbi jdbi =
        Jdbi.create(
            MysqlClient.createMysqlDataSource(
                configuration.getHost(), configuration.getPort(), username, password));
    MysqlSchemaStore schemaStore =
        new MysqlSchemaStore(
            sourceName,
            configuration.getDatabase(),
            configuration.getArchiveDatabase(),
            jdbi,
            metrics);
    MysqlSchemaDatabase schemaDatabase = new MysqlSchemaDatabase(sourceName, jdbi, metrics);

    return new MysqlSchemaManager(sourceName, schemaStore, schemaDatabase, null, null, true);
  }
}
