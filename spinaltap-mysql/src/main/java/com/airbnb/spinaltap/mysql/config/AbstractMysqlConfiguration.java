/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.config;

import com.airbnb.spinaltap.common.config.DestinationConfiguration;
import com.airbnb.spinaltap.common.config.SourceConfiguration;
import java.util.List;

public abstract class AbstractMysqlConfiguration extends SourceConfiguration {
  public AbstractMysqlConfiguration(
      String name,
      String type,
      String instanceTag,
      DestinationConfiguration destinationConfiguration) {
    super(name, type, instanceTag, destinationConfiguration);
  }

  public AbstractMysqlConfiguration(String type, String instanceTag) {
    super(type, instanceTag);
  }

  public abstract String getHost();

  public abstract int getPort();

  public abstract List<String> getCanonicalTableNames();
}
