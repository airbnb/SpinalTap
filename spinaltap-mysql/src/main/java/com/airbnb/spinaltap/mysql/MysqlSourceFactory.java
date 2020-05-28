/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.Source;
import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.airbnb.spinaltap.common.validator.MutationOrderValidator;
import com.airbnb.spinaltap.mysql.binlog_connector.BinaryLogConnectorSource;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManager;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManagerFactory;
import com.airbnb.spinaltap.mysql.validator.EventOrderValidator;
import com.airbnb.spinaltap.mysql.validator.MutationSchemaValidator;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import javax.validation.constraints.Min;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/** Represents a factory for a {@link MysqlSource}. */
@UtilityClass
public class MysqlSourceFactory {
  public Source create(
      @NonNull final MysqlConfiguration configuration,
      @NonNull final String user,
      @NonNull final String password,
      @Min(0) final long serverId,
      @NonNull final Repository<SourceState> backingStateRepository,
      @NonNull final Repository<Collection<SourceState>> stateHistoryRepository,
      final MysqlSchemaManagerFactory schemaManagerFactory,
      @NonNull final MysqlSourceMetrics metrics,
      @Min(0) final long leaderEpoch) {
    final String name = configuration.getName();
    final String host = configuration.getHost();
    final int port = configuration.getPort();

    final BinaryLogClient binlogClient = new BinaryLogClient(host, port, user, password);

    /* Override the global server_id if it is set in MysqlConfiguration
      Allow each source to use a different server_id
    */
    if (configuration.getServerId() != MysqlConfiguration.DEFAULT_SERVER_ID) {
      binlogClient.setServerId(configuration.getServerId());
    } else {
      binlogClient.setServerId(serverId);
    }

    final StateRepository stateRepository =
        new StateRepository(name, backingStateRepository, metrics);
    final StateHistory stateHistory = new StateHistory(name, stateHistoryRepository, metrics);

    final MysqlClient mysqlClient = MysqlClient.create(host, port, user, password);

    final MysqlSchemaManager schemaManager =
        schemaManagerFactory.create(
            name, mysqlClient, configuration.isSchemaVersionEnabled(), metrics);

    final TableCache tableCache =
        new TableCache(schemaManager, configuration.getOverridingDatabase());

    final BinaryLogConnectorSource source =
        new BinaryLogConnectorSource(
            name,
            configuration,
            binlogClient,
            mysqlClient,
            tableCache,
            stateRepository,
            stateHistory,
            schemaManager,
            metrics,
            new AtomicLong(leaderEpoch));

    source.addEventValidator(new EventOrderValidator(metrics::outOfOrder));
    source.addMutationValidator(new MutationOrderValidator(metrics::outOfOrder));
    source.addMutationValidator(new MutationSchemaValidator(metrics::invalidSchema));

    return source;
  }
}
