/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.Source;
import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.airbnb.spinaltap.common.validator.MutationOrderValidator;
import com.airbnb.spinaltap.mysql.binlog_connector.BinaryLogConnectorSource;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import com.airbnb.spinaltap.mysql.schema.CachedMysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.LatestMysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.MysqlDDLHistoryStore;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaDatabase;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaStoreManager;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaTracker;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaUtil;
import com.airbnb.spinaltap.mysql.schema.MysqlTableSchema;
import com.airbnb.spinaltap.mysql.schema.SchemaStore;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.airbnb.spinaltap.mysql.validator.EventOrderValidator;
import com.airbnb.spinaltap.mysql.validator.MutationSchemaValidator;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import javax.validation.constraints.Min;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.skife.jdbi.v2.DBI;

/** Represents a factory for a {@link MysqlSource}. */
@UtilityClass
public class MysqlSourceFactory {
  public static Source create(
      @NonNull final MysqlConfiguration configuration,
      @NonNull final String user,
      @NonNull final String password,
      @Min(0) final long serverId,
      @NonNull final Repository<SourceState> backingStateRepository,
      @NonNull final Repository<Collection<SourceState>> stateHistoryRepository,
      @NonNull final MysqlSchemaStoreConfiguration schemaStoreConfig,
      @NonNull final MysqlSourceMetrics metrics,
      @Min(0) final long leaderEpoch) {
    final String name = configuration.getName();
    final String host = configuration.getHost();
    final int port = configuration.getPort();
    final DBI mysqlDBI = MysqlSchemaUtil.createMysqlDBI(host, port, user, password, null);

    final BinaryLogClient client = new BinaryLogClient(host, port, user, password);

    /* Override the global server_id if it is set in MysqlConfiguration
      Allow each source to use a different server_id
    */
    if (configuration.getServerId() != MysqlConfiguration.DEFAULT_SERVER_ID) {
      client.setServerId(configuration.getServerId());
    } else {
      client.setServerId(serverId);
    }

    final DataSource dataSource = new DataSource(host, port, name);

    final StateRepository stateRepository =
        new StateRepository(name, backingStateRepository, metrics);
    final StateHistory stateHistory = new StateHistory(name, stateHistoryRepository, metrics);

    final LatestMysqlSchemaStore schemaReader = new LatestMysqlSchemaStore(name, mysqlDBI, metrics);

    SchemaStore<MysqlTableSchema> schemaStore;
    SchemaTracker schemaTracker;

    if (configuration.isSchemaVersionEnabled()) {
      final DBI schemaStoreDBI =
          MysqlSchemaUtil.createMysqlDBI(
              schemaStoreConfig.getHost(),
              schemaStoreConfig.getPort(),
              user,
              password,
              schemaStoreConfig.getDatabase());
      final MysqlSchemaStore mysqlSchemaStore =
          new MysqlSchemaStore(
              name, schemaStoreDBI, schemaStoreConfig.getArchiveDatabase(), metrics);

      final DBI schemaDatabaseDBI =
          MysqlSchemaUtil.createMysqlDBI(
              schemaStoreConfig.getHost(), schemaStoreConfig.getPort(), user, password, null);
      final MysqlSchemaDatabase schemaDatabase =
          new MysqlSchemaDatabase(name, schemaDatabaseDBI, metrics);

      final DBI ddlHistoryStoreDBI =
          MysqlSchemaUtil.createMysqlDBI(
              schemaStoreConfig.getHost(),
              schemaStoreConfig.getPort(),
              user,
              password,
              schemaStoreConfig.getDdlHistoryStoreDatabase());
      final MysqlDDLHistoryStore ddlHistoryStore =
          new MysqlDDLHistoryStore(
              name, ddlHistoryStoreDBI, schemaStoreConfig.getArchiveDatabase(), metrics);

      if (!mysqlSchemaStore.isCreated()) {
        MysqlSchemaStoreManager schemaStoreManager =
            new MysqlSchemaStoreManager(
                name, schemaReader, mysqlSchemaStore, schemaDatabase, ddlHistoryStore);
        schemaStoreManager.bootstrapAll();
      }

      schemaStore = new CachedMysqlSchemaStore(name, mysqlSchemaStore, metrics);
      schemaTracker = new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);
    } else {
      schemaStore = schemaReader;
      schemaTracker = (event) -> {};
    }

    final TableCache tableCache = new TableCache(schemaStore);

    final BinaryLogConnectorSource source =
        new BinaryLogConnectorSource(
            name,
            dataSource,
            client,
            new HashSet<>(configuration.getCanonicalTableNames()),
            tableCache,
            stateRepository,
            stateHistory,
            configuration.getInitialBinlogFilePosition(),
            schemaTracker,
            metrics,
            new AtomicLong(leaderEpoch),
            configuration.getSocketTimeoutInSeconds());

    source.addEventValidator(new EventOrderValidator(metrics::outOfOrder));
    source.addMutationValidator(new MutationOrderValidator(metrics::outOfOrder));
    source.addMutationValidator(new MutationSchemaValidator(metrics::invalidSchema));

    return source;
  }
}
