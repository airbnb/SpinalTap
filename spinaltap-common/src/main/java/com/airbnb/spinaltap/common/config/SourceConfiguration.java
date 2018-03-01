/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Base Configuration for a Source */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceConfiguration {
  private static int DEFAULT_REPLICAS = 3;
  private static int DEFAULT_PARTITIONS = 1;

  public SourceConfiguration(
      String name,
      String type,
      String instanceTag,
      DestinationConfiguration destinationConfiguration) {
    this.name = name;
    this.type = type;
    this.instanceGroupTag = instanceTag;
    this.destinationConfiguration = destinationConfiguration;
  }

  public SourceConfiguration(String type, String instanceTag) {
    this.type = type;
    this.instanceGroupTag = instanceTag;
  }

  @NotNull @JsonProperty private String name;

  @Min(1)
  @JsonProperty
  private int replicas = DEFAULT_REPLICAS;

  @Min(1)
  @JsonProperty
  private int partitions = DEFAULT_PARTITIONS;

  @JsonProperty("type")
  private String type;

  @JsonProperty("instance_group_tag")
  private String instanceGroupTag;

  @JsonProperty("destination")
  private DestinationConfiguration destinationConfiguration = new DestinationConfiguration();
}
