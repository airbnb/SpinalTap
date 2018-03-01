/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License").  See License in the project root for license information.
 */

package com.airbnb.spinaltap.kafka

import java.util.Properties

import kafka.server.KafkaConfig

/**
 * LinkedIn integration test harness for Kafka
 * This is simply a copy of open source code, we do this because java does not support trait, we are making it abstract
 * class so user java test class can extend it.
 */
abstract class AbstractKafkaIntegrationTestHarness extends AbstractKafkaServerTestHarness {

  def generateConfigs() =
    TestUtils.createBrokerConfigs(clusterSize(), zkConnect, enableControlledShutdown = false).map(KafkaConfig.fromProps(_, overridingProps()))

  /**
   * User can override this method to return the number of brokers they want.
   * By default only one broker will be launched.
   * @return the number of brokers needed in the Kafka cluster for the test.
   */
  def clusterSize(): Int = 1

  /**
   * User can override this method to apply customized configurations to the brokers.
   * By default the only configuration is number of partitions when topics get automatically created. The default value
   * is 1.
   * @return The configurations to be used by brokers.
   */
  def overridingProps(): Properties = {
    val props = new Properties()
    props.setProperty(KafkaConfig.NumPartitionsProp, 1.toString)
    props
  }

  /**
   * Returns the bootstrap servers configuration string to be used by clients.
   * @return bootstrap servers string.
   */
  def bootstrapServers(): String = super.bootstrapUrl

  /**
   * This method should be defined as @beforeMethod.
   */
  override def setUp() {
    super.setUp()
  }

  /**
   * This method should be defined as @AfterMethod.
   */
  override def tearDown() {
    super.tearDown()
  }

}
