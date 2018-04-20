/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.common.pipe.PipeManager;
import com.airbnb.spinaltap.kafka.KafkaDestinationBuilder;
import com.airbnb.spinaltap.mysql.MySQLPipeFactory;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

@Slf4j
public class SpinalTapStandaloneApp {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      log.error("Usage: SpinalTapStandaloneApp <config.yaml>");
      System.exit(1);
    }

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    SpinalTapStandaloneConfiguration config =
        objectMapper.readValue(new File(args[0]), SpinalTapStandaloneConfiguration.class);

    MySQLPipeFactory mySQLPipeFactory =
        new MySQLPipeFactory(
            config.getMysqlUser(),
            config.getMysqlPassword(),
            config.getMysqlServerId(),
            () -> new KafkaDestinationBuilder<>(config.getKafkaProducerConfig()),
            config.getMysqlSchemaStoreConfig(),
            new TaggedMetricRegistry());

    CuratorFramework zkClient =
        CuratorFrameworkFactory.builder()
            .namespace(config.getZkNamespace())
            .connectString(config.getZkConnectionString())
            .retryPolicy(new ExponentialBackoffRetry(100, 3))
            .build();

    ZookeeperRepositoryFactory zkRepositoryFactory = new ZookeeperRepositoryFactory(zkClient);

    zkClient.start();

    PipeManager pipeManager = new PipeManager();

    for (MysqlConfiguration mysqlSourceConfig : config.getMysqlSources()) {
      String sourceName = mysqlSourceConfig.getName();
      String partitionName = String.format("%s_0", sourceName);
      pipeManager.addPipes(
          sourceName,
          partitionName,
          mySQLPipeFactory.createPipes(mysqlSourceConfig, partitionName, zkRepositoryFactory, 0));
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> pipeManager.stop()));
  }
}
