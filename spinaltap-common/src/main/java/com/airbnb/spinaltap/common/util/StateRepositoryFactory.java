/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import com.airbnb.spinaltap.common.source.SourceState;
import java.util.Collection;

/** Factory for {@link Repository}s that store {@link SourceState} objects. */
public interface StateRepositoryFactory {
  /**
   * @return the {@link Repository} of the {@link SourceState} object for a given resource and
   *     partition.
   */
  Repository<SourceState> getStateRepository(String resourceName, String partitionName);

  /**
   * @return the {@link Repository} of the history of {@link SourceState} objects for a given
   *     resource and partition.
   */
  Repository<Collection<SourceState>> getStateHistoryRepository(
      String resourceName, String partitionName);
}
