/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License").  See License in the project root for license information.
 */

package com.airbnb.spinaltap.kafka

import javax.security.auth.login.Configuration

import kafka.utils.{CoreUtils, Logging, ZkUtils}
import org.apache.kafka.common.security.JaasUtils
import java.io.IOException
import java.net.{SocketTimeoutException, Socket, InetAddress, InetSocketAddress}

/**
 * Zookeeper test harness.
 * This is simply a copy of open source code, we do this because java does not support trait, we are making it abstract
 * class so user java test class can extend it.
 */
abstract class AbstractZookeeperTestHarness extends Logging {

  val zkConnectionTimeout = 6000
  val zkSessionTimeout = 6000

  var zkUtils: ZkUtils = null
  var zookeeper: EmbeddedZookeeper = null

  def zkPort: Int = zookeeper.port

  def zkConnect: String = s"127.0.0.1:$zkPort"

  def setUp() {
    zookeeper = new EmbeddedZookeeper()
    zkUtils = ZkUtils(zkConnect, zkSessionTimeout, zkConnectionTimeout, JaasUtils.isZkSecurityEnabled)
  }

  def tearDown() {
    if (zkUtils != null)
      CoreUtils.swallow(zkUtils.close())
    if (zookeeper != null)
      CoreUtils.swallow(zookeeper.shutdown())

    def isDown: Boolean = {
      try {
        sendStat("127.0.0.1", zkPort, 3000)
        false
      } catch {
        case _: Throwable =>
          debug("Server is down")
          true
      }
    }

    Iterator.continually(isDown).exists(identity)

    Configuration.setConfiguration(null)
  }

  /**
   * Copied from kafka.zk.ZkFourLetterWords.scala which is NOT included in the kafka-clients version
   * we are using.
   */
  def sendStat(host: String, port: Int, timeout: Int) {
    val hostAddress =
      if (host != null) new InetSocketAddress(host, port)
      else new InetSocketAddress(InetAddress.getByName(null), port)
    val sock = new Socket()
    try {
      sock.connect(hostAddress, timeout)
      val outStream = sock.getOutputStream
      outStream.write("stat".getBytes)
      outStream.flush()
    } catch {
      case e: SocketTimeoutException => throw new IOException("Exception while sending 4lw")
    } finally {
      sock.close
    }
  }
}
