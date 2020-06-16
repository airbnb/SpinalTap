/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.common.pipe.PipeManager;
import com.airbnb.spinaltap.kafka.KafkaDestinationBuilder;
import com.airbnb.spinaltap.mysql.MysqlPipeFactory;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManagerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/** A standalone single-node application to run SpinalTap process. */
@Slf4j
public final class SpinalTapStandaloneApp {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      log.error("Usage: SpinalTapStandaloneApp <config.yaml>");
      System.exit(1);
    }

    final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    final SpinalTapStandaloneConfiguration config =
        objectMapper.readValue(new File(args[0]), SpinalTapStandaloneConfiguration.class);

    final MysqlPipeFactory mysqlPipeFactory = createMysqlPipeFactory(config);
    final ZookeeperRepositoryFactory zkRepositoryFactory = createZookeeperRepositoryFactory(config);
    final PipeManager pipeManager = new PipeManager();

    for (MysqlConfiguration mysqlSourceConfig : config.getMysqlSources()) {
      final String sourceName = mysqlSourceConfig.getName();
      final String partitionName = String.format("%s_0", sourceName);
      pipeManager.addPipes(
          sourceName,
          partitionName,
          mysqlPipeFactory.createPipes(mysqlSourceConfig, partitionName, zkRepositoryFactory, 0));
    }

    Runtime.getRuntime().addShutdownHook(new Thread(pipeManager::stop));
  }

  private static MysqlPipeFactory createMysqlPipeFactory(
      final SpinalTapStandaloneConfiguration config) {
    return new MysqlPipeFactory(
        config.getMysqlUser(),
        config.getMysqlPassword(),
        config.getMysqlServerId(),
        config.getTlsConfiguration(),
        ImmutableMap.of(
            "kafka", () -> new KafkaDestinationBuilder<>(config.getKafkaProducerConfig())),
        new MysqlSchemaManagerFactory(
            config.getMysqlUser(),
            config.getMysqlPassword(),
            config.getMysqlSchemaStoreConfig(),
            config.getTlsConfiguration()),
        new TaggedMetricRegistry());
  }

  private static ZookeeperRepositoryFactory createZookeeperRepositoryFactory(
      final SpinalTapStandaloneConfiguration config) {
    final CuratorFramework zkClient =
        CuratorFrameworkFactory.builder()
            .namespace(config.getZkNamespace())
            .connectString(config.getZkConnectionString())
            .retryPolicy(new ExponentialBackoffRetry(100, 3))
            .build();

    zkClient.start();

    return new ZookeeperRepositoryFactory(zkClient);
  }
}
