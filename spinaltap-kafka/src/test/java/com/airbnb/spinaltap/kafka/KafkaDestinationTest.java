/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.kafka;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.jitney.event.spinaltap.v1.MutationType;
import com.airbnb.spinaltap.common.destination.DestinationMetrics;
import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.mutation.MysqlDeleteMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.MysqlUpdateMutation;
import com.airbnb.spinaltap.mysql.mutation.mapper.ThriftMutationMapper;
import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import kafka.admin.AdminUtils;
import kafka.server.KafkaConfig;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class KafkaDestinationTest extends AbstractKafkaIntegrationTestHarness {
  private static final int SESSION_TIMEOUT_MS = 10000;
  private static final int CONNECTION_TIMEOUT_MS = 10000;
  private static final ZkSerializer ZK_SERIALIZER = ZKStringSerializer$.MODULE$;
  private static final String SOURCE_NAME = "localhost";
  private static final String HOSTNAME = "127.0.0.1";
  private static final String DATABASE = "database";
  private static final String TABLE = "table";
  private static final String TOPIC =
      "spinaltap" + "." + SOURCE_NAME + "-" + DATABASE + "-" + TABLE;
  private static final ThreadLocal<TDeserializer> deserializer =
      ThreadLocal.withInitial(() -> new TDeserializer((new TBinaryProtocol.Factory())));

  private final DestinationMetrics metrics =
      new DestinationMetrics("test", "test", new TaggedMetricRegistry());

  @Before
  public void setUp() {
    super.setUp();
  }

  @After
  public void tearDown() {
    super.tearDown();
  }

  @Override
  public Properties overridingProps() {
    Properties props = new Properties();
    props.setProperty(KafkaConfig.AutoCreateTopicsEnableProp(), Boolean.toString(false));
    return props;
  }

  @Override
  public int clusterSize() {
    return 3;
  }

  private void createKafkaTopic(String topicName) throws Exception {
    ZkConnection zkConn = null;
    ZkClient zkClient = null;
    try {
      zkClient =
          new ZkClient(
              "localhost:" + zkPort(), SESSION_TIMEOUT_MS, CONNECTION_TIMEOUT_MS, ZK_SERIALIZER);
      zkConn = new ZkConnection("localhost:" + zkPort());
      Properties props = new Properties();
      props.setProperty("min.insync.replicas", "2");
      AdminUtils.createTopic(new ZkUtils(zkClient, zkConn, false), topicName, 1, 3, props);
    } catch (Exception ex) {
      logger().error("Kafka topic creation failed due to " + ex.getLocalizedMessage());
      // We need to abort upon topic creation failure.
      throw ex;
    } finally {
      if (zkClient != null) zkClient.close();
      if (zkConn != null) zkConn.close();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void KafkaDestination() throws Exception {
    createKafkaTopic(TOPIC);
    KafkaProducerConfiguration configs = new KafkaProducerConfiguration(this.bootstrapServers());
    KafkaDestination kafkaDestination = new KafkaDestination(null, configs, x -> x, metrics, 0L);
    List<Mutation> messages = new ArrayList<>();
    messages.add(createMutation(MutationType.INSERT));
    messages.add(createMutation(MutationType.UPDATE));
    messages.add(createMutation(MutationType.DELETE));
    kafkaDestination.publish(messages);

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", this.bootstrapServers());
    props.setProperty(
        "key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    props.setProperty(
        "value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    KafkaConsumer<byte[], byte[]> kafkaConsumer = new KafkaConsumer<>(props);
    kafkaConsumer.assign(Collections.singletonList(new TopicPartition(TOPIC, 0)));
    kafkaConsumer.seekToBeginning(new TopicPartition(TOPIC, 0));
    List<ConsumerRecords<byte[], byte[]>> records = new ArrayList<>();
    ConsumerRecords<byte[], byte[]> record;
    long startMs = current();
    while (current() - startMs <= 10000L) {
      record = kafkaConsumer.poll(1000L);
      records.add(record);
      if (records.size() == 3) break;
    }
    Assert.assertEquals(records.size(), 3);

    for (ConsumerRecords<byte[], byte[]> consumerRecords : records) {
      for (ConsumerRecord<byte[], byte[]> consumerRecord : consumerRecords) {
        com.airbnb.jitney.event.spinaltap.v1.Mutation mutation =
            getMutation(consumerRecord.value());
        switch (mutation.getType()) {
          case INSERT:
            Assert.assertEquals(mutation, createMutation(MutationType.INSERT));
            break;
          case UPDATE:
            Assert.assertEquals(mutation, createMutation(MutationType.UPDATE));
            break;
          case DELETE:
            Assert.assertEquals(mutation, createMutation(MutationType.DELETE));
            break;
        }
      }
    }
    kafkaDestination.close();
    kafkaConsumer.close();
  }

  private long current() {
    return System.currentTimeMillis();
  }

  private Mutation getMutation(byte[] payload) throws Exception {
    Mutation mutation = new Mutation();
    deserializer.get().deserialize(mutation, payload);
    return mutation;
  }

  private Mutation createMutation(MutationType type) {
    Mapper<com.airbnb.spinaltap.Mutation<?>, ? extends TBase<?, ?>> thriftMutationMapper =
        ThriftMutationMapper.create("spinaltap");
    Table table =
        new Table(
            0L,
            TABLE,
            DATABASE,
            null,
            ImmutableList.of(new ColumnMetadata("id", ColumnDataType.LONGLONG, true, 0, "")),
            ImmutableList.of("id"));
    MysqlMutationMetadata metadata =
        new MysqlMutationMetadata(
            new DataSource(HOSTNAME, 0, SOURCE_NAME),
            new BinlogFilePos(),
            table,
            0L,
            0L,
            0L,
            null,
            null,
            0L,
            0);
    Row row =
        new Row(
            table,
            ImmutableMap.of(
                "id", new Column(new ColumnMetadata("id", ColumnDataType.LONGLONG, true, 0, ""), 1L)));
    MysqlMutation mutation;
    switch (type) {
      case INSERT:
        mutation = new MysqlInsertMutation(metadata, row);
        break;
      case UPDATE:
        mutation = new MysqlUpdateMutation(metadata, row, row);
        break;
      case DELETE:
        mutation = new MysqlDeleteMutation(metadata, row);
        break;
      default:
        mutation = null;
    }

    return (Mutation) (thriftMutationMapper.map(mutation));
  }
}
