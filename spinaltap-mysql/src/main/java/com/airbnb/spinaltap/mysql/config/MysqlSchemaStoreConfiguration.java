/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.config;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents configuration for a {@link com.airbnb.spinaltap.mysql.schema.MysqlSchemaStore}.
 */
@Data
public class MysqlSchemaStoreConfiguration {
  @NotNull @JsonProperty private String host;

  @Min(0)
  @Max(65535)
  @JsonProperty
  private int port;

  @NotNull @JsonProperty private String database;

  @NotNull
  @JsonProperty("archive-database")
  private String archiveDatabase;
}
