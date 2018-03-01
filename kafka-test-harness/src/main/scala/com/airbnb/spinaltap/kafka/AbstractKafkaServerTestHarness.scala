/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License").  See License in the project root for license information.
 */

package com.airbnb.spinaltap.kafka

import java.io.File
import java.util
import java.util.Properties

import kafka.common.KafkaException
import kafka.server.{KafkaConfig, KafkaServer}
import kafka.utils.CoreUtils
import org.apache.kafka.common.protocol.SecurityProtocol
import org.apache.kafka.common.security.auth.KafkaPrincipal

import scala.collection.mutable

/**
 * Kafka server integration test harness.
 * This is simply a copy of open source code, we do this because java does not support trait, we are making it abstract
 * class so user java test class can extend it.
 */
abstract class AbstractKafkaServerTestHarness extends AbstractZookeeperTestHarness {
  var instanceConfigs: Seq[KafkaConfig] = null
  var servers: mutable.Buffer[KafkaServer] = null
  var brokerList: String = null
  var alive: Array[Boolean] = null
  val kafkaPrincipalType = KafkaPrincipal.USER_TYPE
  val setClusterAcl: Option[() => Unit] = None

  /**
   * Implementations must override this method to return a set of KafkaConfigs. This method will be invoked for every
   * test and should not reuse previous configurations unless they select their ports randomly when servers are started.
   */
  def generateConfigs(): Seq[KafkaConfig]

  def serverForId(id: Int) = servers.find(s => s.config.brokerId == id)

  def bootstrapUrl: String = brokerList

  protected def securityProtocol: SecurityProtocol = SecurityProtocol.PLAINTEXT

  protected def trustStoreFile: Option[File] = None

  protected def saslProperties: Option[Properties] = None

  override def setUp() {
    super.setUp()
    val configs = generateConfigs()
    if (configs.size <= 0)
      throw new KafkaException("Must supply at least one server config.")
    servers = configs.map(TestUtils.createServer(_)).toBuffer
    brokerList = TestUtils.getBrokerListStrFromServers(servers, securityProtocol)
    alive = new Array[Boolean](servers.length)
    util.Arrays.fill(alive, true)
    // We need to set a cluster ACL in some cases here
    // because of the topic creation in the setup of
    // IntegrationTestHarness. If we don't, then tests
    // fail with a cluster action authorization exception
    // when processing an update metadata request
    // (controller -> broker).
    //
    // The following method does nothing by default, but
    // if the test case requires setting up a cluster ACL,
    // then it needs to be implemented.
    setClusterAcl.foreach(_.apply())
  }

  override def tearDown() {
    if (servers != null) {
      servers.foreach(_.shutdown())
      servers.foreach(server => CoreUtils.rm(server.config.logDirs))
    }
    super.tearDown()
  }

  /**
   * Pick a broker at random and kill it if it isn't already dead
   * Return the id of the broker killed
   */
  def killRandomBroker(): Int = {
    val index = TestUtils.random.nextInt(servers.length)
    if (alive(index)) {
      servers(index).shutdown()
      servers(index).awaitShutdown()
      alive(index) = false
    }
    index
  }

  /**
   * Restart any dead brokers
   */
  def restartDeadBrokers() {
    for (i <- 0 until servers.length if !alive(i)) {
      servers(i).startup()
      alive(i) = true
    }
  }
}
