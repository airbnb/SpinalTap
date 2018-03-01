/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License").  See License in the project root for license information.
 */

package com.airbnb.spinaltap.kafka

import java.net.InetSocketAddress

import kafka.utils.CoreUtils
import org.apache.kafka.common.utils.Utils
import org.apache.zookeeper.server.{NIOServerCnxnFactory, ZooKeeperServer}

/**
 * This is a copy of open source Kafka embedded zookeeper class. We put it here to avoid dependency on o.a.k.test jar.
 */
class EmbeddedZookeeper() {
  val snapshotDir = TestUtils.tempDir()
  val logDir = TestUtils.tempDir()
  val tickTime = 500
  val zookeeper = new ZooKeeperServer(snapshotDir, logDir, tickTime)
  val factory = new NIOServerCnxnFactory()
  private val addr = new InetSocketAddress("127.0.0.1", TestUtils.RandomPort)
  factory.configure(addr, 0)
  factory.startup(zookeeper)
  val port = zookeeper.getClientPort

  def shutdown() {
    CoreUtils.swallow(zookeeper.shutdown())
    CoreUtils.swallow(factory.shutdown())
    Utils.delete(logDir)
    Utils.delete(snapshotDir)
  }

}
