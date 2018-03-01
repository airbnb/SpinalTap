/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KafkaProducerConfiguration {
  @JsonProperty("bootstrap_servers")
  private String bootstrapServers;

  public KafkaProducerConfiguration(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }
}
