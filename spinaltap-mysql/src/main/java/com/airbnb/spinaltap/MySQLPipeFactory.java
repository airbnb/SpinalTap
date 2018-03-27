/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.spinaltap.common.config.DestinationConfiguration;
import com.airbnb.spinaltap.common.destination.Destination;
import com.airbnb.spinaltap.common.destination.DestinationBuilder;
import com.airbnb.spinaltap.common.pipe.AbstractPipeFactory;
import com.airbnb.spinaltap.common.pipe.Pipe;
import com.airbnb.spinaltap.common.pipe.PipeMetrics;
import com.airbnb.spinaltap.common.source.Source;
import com.airbnb.spinaltap.common.util.RepositoryFactory;
import com.airbnb.spinaltap.mysql.MysqlDestinationMetrics;
import com.airbnb.spinaltap.mysql.MysqlSource;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import com.airbnb.spinaltap.mysql.mutation.MysqlKeyProvider;
import com.airbnb.spinaltap.mysql.mutation.mapper.ThriftMutationMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

@Slf4j
public class MySQLPipeFactory extends AbstractPipeFactory<MysqlConfiguration> {
  public static final String MYSQL_JITNEY_TOPIC = "spinaltap";

  private final String mysqlUser;
  private final String mysqlPassword;
  private final long mysqlServerId;
  private final Supplier<DestinationBuilder<Mutation>> destinationBuilderSupplier;
  private final MysqlSchemaStoreConfiguration schemaStoreConfig;

  public MySQLPipeFactory(
      String mysqlUser,
      String mysqlPassword,
      long mysqlServerId,
      Supplier<DestinationBuilder<Mutation>> destinationBuilderSupplier,
      MysqlSchemaStoreConfiguration schemaStoreConfig,
      TaggedMetricRegistry metricRegistry) {
    super(metricRegistry);
    this.mysqlUser = mysqlUser;
    this.mysqlPassword = mysqlPassword;
    this.mysqlServerId = mysqlServerId;
    this.destinationBuilderSupplier = destinationBuilderSupplier;
    this.schemaStoreConfig = schemaStoreConfig;
  }

  @Override
  public List<Pipe> createPipes(
      MysqlConfiguration sourceConfig,
      String partitionName,
      RepositoryFactory repositoryFactory,
      long leaderEpoch)
      throws Exception {
    return Collections.singletonList(
        create(sourceConfig, partitionName, repositoryFactory, leaderEpoch));
  }

  private Pipe create(
      MysqlConfiguration sourceConfig,
      String partitionName,
      RepositoryFactory repositoryFactory,
      long leaderEpoch)
      throws Exception {
    String sourceName = sourceConfig.getName();

    Source source =
        MysqlSource.create(
            sourceName,
            sourceConfig.getHost(),
            sourceConfig.getPort(),
            sourceConfig.getSocketTimeoutInSeconds(),
            mysqlUser,
            mysqlPassword,
            // Use a different server_id for REPLICAS in case the same database is configured as
            // both MASTER and REPLICA
            mysqlServerId + sourceConfig.getHostRole().ordinal() * 100,
            sourceConfig.getCanonicalTableNames(),
            repositoryFactory.getStateRepository(sourceName, partitionName),
            repositoryFactory.getStateHistoryRepository(sourceName, partitionName),
            sourceConfig.getInitialBinlogFilePosition(),
            sourceConfig.isSchemaVersionEnabled(),
            schemaStoreConfig,
            new MysqlSourceMetrics(sourceConfig.getName(), metricRegistry),
            leaderEpoch);

    DestinationConfiguration destConfig = sourceConfig.getDestinationConfiguration();

    Preconditions.checkState(
        !(sourceConfig.getHostRole().equals(MysqlConfiguration.HostRole.MIGRATION)
            && destConfig.getPoolSize() > 0),
        String.format("Destination pool size is not 0 for MIGRATION source %s", sourceName));

    Destination destination =
        destinationBuilderSupplier
            .get()
            .withTopicNamePrefix(MysqlConfiguration.MYSQL_TOPICS.get(sourceConfig.getHostRole()))
            .withMapper(ThriftMutationMapper.create(getHostName()))
            .withMetrics(new MysqlDestinationMetrics(sourceConfig.getName(), metricRegistry))
            .withBuffer(destConfig.getBufferSize())
            .withPool(destConfig.getPoolSize(), MysqlKeyProvider.INSTANCE)
            .withValidation()
            .withLargeMessage(sourceConfig.isLargeMessageEnabled())
            .withDelaySendMs(sourceConfig.getDelaySendMs())
            .build();

    PipeMetrics metrics = new PipeMetrics(source.getName(), metricRegistry);

    return new Pipe(source, destination, metrics);
  }
}
