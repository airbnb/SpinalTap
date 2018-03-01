/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import com.airbnb.spinaltap.common.source.SourceState;
import java.util.Collection;

public interface RepositoryFactory {
  Repository<SourceState> getStateRepository(String resourceName, String partitionName);

  Repository<Collection<SourceState>> getStateHistoryRepository(
      String resourceName, String partitionName);
}
