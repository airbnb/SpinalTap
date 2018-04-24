/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.config;

import com.airbnb.spinaltap.common.config.DestinationConfiguration;
import com.airbnb.spinaltap.common.config.SourceConfiguration;
import com.airbnb.spinaltap.mysql.MysqlSource;

import java.util.List;

/**
 * Represents the base configuration for a {@link MysqlSource}.
 */
public abstract class AbstractMysqlConfiguration extends SourceConfiguration {
  public AbstractMysqlConfiguration(
      final String name,
      final String type,
      final String instanceTag,
      final DestinationConfiguration destinationConfiguration) {
    super(name, type, instanceTag, destinationConfiguration);
  }

  public AbstractMysqlConfiguration(final String type,final String instanceTag) {
    super(type, instanceTag);
  }

  public abstract String getHost();

  public abstract int getPort();

  public abstract List<String> getCanonicalTableNames();
}
