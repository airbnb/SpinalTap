/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents a {@code Source} configuration. */
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

  /**
   * The source name.
   */
  @NotNull @JsonProperty private String name;

  /**
   * The number of replicas to stream from the source.
   *
   * <p> Note: This is only applicable if a cluster solution is employed. A Master-Replica state
   * transition model is recommended, where one cluster instance (master) is streaming events
   * from a given source at any point in time. This is required to ensure ordering guarantees.
   * Replicas will be promoted to Master in case of failure. Increasing number of replicas can be
   * used to improve fault tolerance. </p>
   */
  @Min(1)
  @JsonProperty
  private int replicas = DEFAULT_REPLICAS;

  /**
   * The number of stream partitions for a given source.
   *
   * <p> Note: This is only applicable if a cluster solution is employed.</p>
   */
  @Min(1)
  @JsonProperty
  private int partitions = DEFAULT_PARTITIONS;

  /**
   * The source type (ex: MySQL, DynamoDB)
   */
  @JsonProperty("type")
  private String type;

  /**
   * The group tag for cluster instances of the given source.
   *
   * <p> Note: This is only applicable if a cluster solution is employed. Tagging is used
   * to indicate the instances streaming a particular source. </p>
   */
  @JsonProperty("instance_group_tag")
  private String instanceGroupTag;

  /**
   * The destination configuration for the specified source.
   */
  @JsonProperty("destination")
  private DestinationConfiguration destinationConfiguration = new DestinationConfiguration();
}
