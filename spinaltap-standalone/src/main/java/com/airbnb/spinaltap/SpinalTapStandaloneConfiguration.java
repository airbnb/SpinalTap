/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import com.airbnb.spinaltap.kafka.KafkaProducerConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpinalTapStandaloneConfiguration {
  public static final int DEFAULT_MYSQL_SERVER_ID = 65535;

  @NotNull
  @JsonProperty("zk-connection-string")
  private String zkConnectionString;

  @NotNull
  @JsonProperty("zk-namespace")
  private String zkNamespace;

  @NotNull
  @JsonProperty("kafka-config")
  private KafkaProducerConfiguration kafkaProducerConfig;

  @NotNull
  @JsonProperty("mysql-user")
  private String mysqlUser;

  @NotNull
  @JsonProperty("mysql-password")
  private String mysqlPassword;

  @NotNull
  @JsonProperty("mysql-server-id")
  private long mysqlServerId = DEFAULT_MYSQL_SERVER_ID;

  @JsonProperty("mysql-schema-store")
  private MysqlSchemaStoreConfiguration mysqlSchemaStoreConfig;

  @NotNull
  @JsonProperty("mysql-sources")
  private List<MysqlConfiguration> mysqlSources;
}
