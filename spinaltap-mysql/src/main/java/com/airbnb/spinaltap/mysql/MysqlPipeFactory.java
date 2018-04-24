/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.spinaltap.common.config.DestinationConfiguration;
import com.airbnb.spinaltap.common.destination.Destination;
import com.airbnb.spinaltap.common.destination.DestinationBuilder;
import com.airbnb.spinaltap.common.pipe.AbstractPipeFactory;
import com.airbnb.spinaltap.common.pipe.Pipe;
import com.airbnb.spinaltap.common.pipe.PipeMetrics;
import com.airbnb.spinaltap.common.source.Source;
import com.airbnb.spinaltap.common.util.StateRepositoryFactory;
import com.airbnb.spinaltap.mysql.binlog_connector.BinaryLogConnectorSource;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import com.airbnb.spinaltap.mysql.mutation.MysqlKeyProvider;
import com.airbnb.spinaltap.mysql.mutation.mapper.ThriftMutationMapper;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.validation.constraints.Min;

import com.google.common.base.Preconditions;

/** Represents a factory implement for {@link Pipe}s streaming from a {@link BinaryLogConnectorSource}. */
@Slf4j
public final class MysqlPipeFactory extends AbstractPipeFactory<MysqlConfiguration> {
  public static final String DEFAULT_MYSQL_TOPIC_PREFIX = "spinaltap";

  private final String mysqlUser;
  private final String mysqlPassword;
  private final long mysqlServerId;
  private final Supplier<DestinationBuilder<Mutation>> destinationBuilderSupplier;
  private final MysqlSchemaStoreConfiguration schemaStoreConfig;

  public MysqlPipeFactory(
      @NonNull final String mysqlUser,
      @NonNull final String mysqlPassword,
      @NonNull final long mysqlServerId,
      @NonNull final Supplier<DestinationBuilder<Mutation>> destinationBuilderSupplier,
      @NonNull final MysqlSchemaStoreConfiguration schemaStoreConfig,
      @NonNull final TaggedMetricRegistry metricRegistry) {
    super(metricRegistry);
    this.mysqlUser = mysqlUser;
    this.mysqlPassword = mysqlPassword;
    this.mysqlServerId = mysqlServerId;
    this.destinationBuilderSupplier = destinationBuilderSupplier;
    this.schemaStoreConfig = schemaStoreConfig;
  }

  @Override
  public List<Pipe> createPipes(
      @NonNull final MysqlConfiguration sourceConfig,
      @NonNull final String partitionName,
      @NonNull final StateRepositoryFactory repositoryFactory,
      @Min(0) final long leaderEpoch)
      throws Exception {
    return Collections.singletonList(
        create(sourceConfig, partitionName, repositoryFactory, leaderEpoch));
  }

  private Pipe create(
      final MysqlConfiguration sourceConfig,
      final String partitionName,
      final StateRepositoryFactory repositoryFactory,
      final long leaderEpoch)
      throws Exception {
    final Source source = createSource(sourceConfig, repositoryFactory, partitionName, leaderEpoch);
    final DestinationConfiguration destinationConfig = sourceConfig.getDestinationConfiguration();

    Preconditions.checkState(
        !(sourceConfig.getHostRole().equals(MysqlConfiguration.HostRole.MIGRATION)
            && destinationConfig.getPoolSize() > 0),
        String.format(
            "Destination pool size is not 0 for MIGRATION source %s", sourceConfig.getName()));

    final Destination destination = createDestination(sourceConfig, destinationConfig);
    return new Pipe(source, destination, new PipeMetrics(source.getName(), metricRegistry));
  }

  private Source createSource(
      final MysqlConfiguration configuration,
      final StateRepositoryFactory repositoryFactory,
      final String partitionName,
      final long leaderEpoch) {
    return MysqlSourceFactory.create(
        configuration,
        mysqlUser,
        mysqlPassword,
        // Use a different server_id for REPLICAS in case the same database is configured as
        // both MASTER and REPLICA
        mysqlServerId + configuration.getHostRole().ordinal() * 100,
        repositoryFactory.getStateRepository(configuration.getName(), partitionName),
        repositoryFactory.getStateHistoryRepository(configuration.getName(), partitionName),
        schemaStoreConfig,
        new MysqlSourceMetrics(configuration.getName(), metricRegistry),
        leaderEpoch);
  }

  private Destination createDestination(
      final MysqlConfiguration sourceConfiguration,
      final DestinationConfiguration destinationConfiguration) {
    return destinationBuilderSupplier
        .get()
        .withTopicNamePrefix(MysqlConfiguration.MYSQL_TOPICS.get(sourceConfiguration.getHostRole()))
        .withMapper(ThriftMutationMapper.create(getHostName()))
        .withMetrics(new MysqlDestinationMetrics(sourceConfiguration.getName(), metricRegistry))
        .withBuffer(destinationConfiguration.getBufferSize())
        .withPool(destinationConfiguration.getPoolSize(), MysqlKeyProvider.INSTANCE)
        .withValidation()
        .withLargeMessage(sourceConfiguration.isLargeMessageEnabled())
        .withDelaySendMs(sourceConfiguration.getDelaySendMs())
        .build();
  }
}
