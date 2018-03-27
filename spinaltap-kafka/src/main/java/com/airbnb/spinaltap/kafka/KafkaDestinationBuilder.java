/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.kafka;

import com.airbnb.spinaltap.common.destination.Destination;
import com.airbnb.spinaltap.common.destination.DestinationBuilder;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TBase;

@RequiredArgsConstructor
public class KafkaDestinationBuilder<T extends TBase<?, ?>> extends DestinationBuilder<T> {
  private final KafkaProducerConfiguration producerConfig;

  @Override
  protected Destination createDestination() {
    return new KafkaDestination<>(topicNamePrefix, producerConfig, mapper, metrics, delaySendMs);
  }
}
