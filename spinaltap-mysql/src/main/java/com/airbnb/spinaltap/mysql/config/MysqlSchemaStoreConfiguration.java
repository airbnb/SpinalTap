/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Represents the configuration for a {@link com.airbnb.spinaltap.mysql.schema.MysqlSchemaStore} */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MysqlSchemaStoreConfiguration {
  @NonNull @JsonProperty private String host;

  @Min(0)
  @Max(65535)
  @JsonProperty
  private int port;

  @JsonProperty("mtls_enabled")
  private boolean mTlsEnabled;

  @NonNull @JsonProperty private String database = "schema_store";

  @NonNull
  @JsonProperty("archive-database")
  private String archiveDatabase = "schema_store_archives";
}
