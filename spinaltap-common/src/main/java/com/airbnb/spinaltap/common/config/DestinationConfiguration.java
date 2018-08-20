/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Represents a {@link com.airbnb.spinaltap.common.destination.Destination} configuration. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DestinationConfiguration {
  public static final String DEFAULT_TYPE = "kafka";
  public static final int DEFAULT_BUFFER_SIZE = 0;
  public static final int DEFAULT_POOL_SIZE = 0;

  /** The destination type. Default to "kafka". */
  @NonNull
  @JsonProperty("type")
  private String type = DEFAULT_TYPE;

  /**
   * The buffer size. If greater than 0, a {@link
   * com.airbnb.spinaltap.common.destination.BufferedDestination} will be constructed.
   */
  @Min(0)
  @JsonProperty("buffer_size")
  private int bufferSize = DEFAULT_BUFFER_SIZE;

  /**
   * The pool size. If greater than 0, a {@link
   * com.airbnb.spinaltap.common.destination.DestinationPool} will be constructed with the specified
   * number of {@link com.airbnb.spinaltap.common.destination.Destination}s.
   */
  @Min(0)
  @JsonProperty("pool_size")
  private int poolSize = DEFAULT_POOL_SIZE;
}
