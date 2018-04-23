/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import com.airbnb.spinaltap.kafka.KafkaProducerConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents the {@link SpinalTapStandaloneApp} configuration. */
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

  /**
   * The kafka producer configuration that is used in {@link
   * com.airbnb.spinaltap.kafka.KafkaDestination}.
   */
  @NotNull
  @JsonProperty("kafka-config")
  private KafkaProducerConfiguration kafkaProducerConfig;

  /**
   * The mysql user to connect to the source binlog.
   *
   * <p>Note: The user should have following grants on the source databases
   *
   * <ul>
   *   <li>SELECT
   *   <li>REPLICATION SLAVE
   *   <li>REPLICATION CLIENT
   *   <li>SHOW VIEW
   * </ul>
   */
  @NotNull
  @JsonProperty("mysql-user")
  private String mysqlUser;

  /** The mysql source user password. */
  @NotNull
  @JsonProperty("mysql-password")
  private String mysqlPassword;

  @NotNull
  @JsonProperty("mysql-server-id")
  private long mysqlServerId = DEFAULT_MYSQL_SERVER_ID;

  /** The mysql schema store configuration to enable tracking schema changes. */
  @JsonProperty("mysql-schema-store")
  private MysqlSchemaStoreConfiguration mysqlSchemaStoreConfig;

  /** The list of mysql sources to stream from. */
  @NotNull
  @JsonProperty("mysql-sources")
  private List<MysqlConfiguration> mysqlSources;
}
