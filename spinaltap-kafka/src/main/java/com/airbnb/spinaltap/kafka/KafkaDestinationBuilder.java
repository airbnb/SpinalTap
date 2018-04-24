/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.kafka;

import com.airbnb.spinaltap.common.destination.Destination;
import com.airbnb.spinaltap.common.destination.DestinationBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TBase;

/** Represents an implement of {@link DestinationBuilder} for {@link KafkaDestination}s. */
@RequiredArgsConstructor
public final class KafkaDestinationBuilder<T extends TBase<?, ?>> extends DestinationBuilder<T> {
  @NonNull private final KafkaProducerConfiguration producerConfig;

  @Override
  protected Destination createDestination() {
    return new KafkaDestination<>(topicNamePrefix, producerConfig, mapper, metrics, delaySendMs);
  }
}
