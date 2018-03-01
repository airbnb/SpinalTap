/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSource {
  private String host;
  private int port;
  private String service;

  @JsonIgnore
  @Getter(lazy = true)
  private final com.airbnb.jitney.event.spinaltap.v1.DataSource thriftDataSource =
      toThriftDataSource(this);

  private static com.airbnb.jitney.event.spinaltap.v1.DataSource toThriftDataSource(
      DataSource dataSource) {
    return new com.airbnb.jitney.event.spinaltap.v1.DataSource(
        dataSource.getHost(), dataSource.getPort(), dataSource.getService());
  }
}
