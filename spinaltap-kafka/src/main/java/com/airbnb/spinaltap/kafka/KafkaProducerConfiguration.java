/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents the Kafka producer configuration used in {@link KafkaDestination}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaProducerConfiguration {
  @JsonProperty("bootstrap_servers")
  private String bootstrapServers;
}
