/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.kafka;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.destination.AbstractDestination;
import com.airbnb.spinaltap.common.destination.DestinationMetrics;
import com.airbnb.spinaltap.common.util.BatchMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;

/**
 * Represents an implement of {@link com.airbnb.spinaltap.common.destination.Destination} using <a
 * href="https://kafka.apache.org">Apache Kafka</a>.
 */
@Slf4j
public final class KafkaDestination<T extends TBase<?, ?>> extends AbstractDestination<T> {
  private static final String DEFAULT_TOPIC_PREFIX = "spinaltap";

  private volatile boolean failed = false;

  private final String topicNamePrefix;
  private final KafkaProducer<byte[], byte[]> kafkaProducer;
  private final Callback callback = new SpinalTapPublishCallback();
  private final ThreadLocal<TSerializer> serializer =
      ThreadLocal.withInitial(() -> new TSerializer((new TBinaryProtocol.Factory())));

  public KafkaDestination(
      final String prefix,
      final KafkaProducerConfiguration producerConfig,
      final BatchMapper<Mutation<?>, T> mapper,
      final DestinationMetrics metrics,
      final long delaySendMs) {
    super(mapper, metrics, delaySendMs);

    topicNamePrefix = Optional.ofNullable(prefix).orElse(DEFAULT_TOPIC_PREFIX);
    Properties props = new Properties();
    setKafkaDefaultConfigs(props, producerConfig.getBootstrapServers());
    kafkaProducer = new KafkaProducer<>(props);
  }

  private void setKafkaDefaultConfigs(Properties props, String bootstrapServers) {
    // For bootstrap.servers.
    props.setProperty("bootstrap.servers", bootstrapServers);
    // For durability.
    props.setProperty("acks", "-1");
    // For in-order delivery.
    props.setProperty("max.in.flight.requests.per.connection", "1");
    // For default serializer.
    props.setProperty(
        "key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    props.setProperty(
        "value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
  }

  @Override
  public void publish(List<T> messages) throws Exception {
    try {
      failed = false;

      messages.forEach(message -> kafkaProducer.send(transform(message), callback));
      kafkaProducer.flush();

      if (failed) {
        throw new Exception("Error when sending event to Kafka.");
      }
    } catch (Exception ex) {
      throw new Exception("Error when sending event to Kafka.");
    }
  }

  /** Transform from TBase to the ProducerRecord. */
  private ProducerRecord<byte[], byte[]> transform(TBase<?, ?> event) throws RuntimeException {
    try {
      String topic = getTopic(event);
      byte[] key = getKey(event);
      byte[] value = serializer.get().serialize(event);
      return new ProducerRecord<>(topic, key, value);
    } catch (TException ex) {
      throw new RuntimeException("Error when transforming event from TBase to ProducerRecord.", ex);
    } catch (Exception ex) {
      throw new RuntimeException("Invalid mutation found when transforming.", ex);
    }
  }

  /** Use the primary key as the key of the ProducerRecord. */
  private byte[] getKey(TBase<?, ?> event) {
    com.airbnb.jitney.event.spinaltap.v1.Mutation mutation =
        ((com.airbnb.jitney.event.spinaltap.v1.Mutation) event);

    Set<String> primaryKeys = mutation.getTable().getPrimaryKey();
    String tableName = mutation.getTable().getName();
    String databaseName = mutation.getTable().getDatabase();
    Map<String, ByteBuffer> entities = mutation.getEntity();
    StringBuilder builder = new StringBuilder(databaseName + ":" + tableName);
    for (String keyComponent : primaryKeys) {
      String component = new String(entities.get(keyComponent).array(), StandardCharsets.UTF_8);
      builder.append(":").append(component);
    }
    return builder.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * The format of the topic for a table from source in database is as follows:
   * [source]-[database]-[table]
   */
  private String getTopic(final TBase<?, ?> event) {
    com.airbnb.jitney.event.spinaltap.v1.Mutation mutation =
        ((com.airbnb.jitney.event.spinaltap.v1.Mutation) event);
    return String.format(
        "%s.%s-%s-%s",
        topicNamePrefix,
        mutation.getDataSource().getSynapseService(),
        mutation.getTable().getDatabase(),
        mutation.getTable().getName());
  }

  /**
   * The callback to mark the asynchronous send result for KafkaProducer. Close the KafkaProducer
   * inside the callback if there is an exception to prevent out-of-order delivery.
   */
  private class SpinalTapPublishCallback implements Callback {
    public void onCompletion(RecordMetadata metadata, Exception exception) {
      if (exception != null) {
        failed = true;
        kafkaProducer.close();
      }
    }
  }
}
