/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.pipe;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.common.config.SourceConfiguration;
import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.StateRepositoryFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPipeFactory<S extends SourceState, T extends SourceConfiguration> {
  private static String HOST_NAME = "unknown";

  protected final TaggedMetricRegistry metricRegistry;

  public abstract List<Pipe> createPipes(
      T sourceConfig,
      String partitionName,
      StateRepositoryFactory<S> repositoryFactory,
      long leaderEpoch)
      throws Exception;

  protected static String getHostName() {
    if ("unknown".equalsIgnoreCase(HOST_NAME)) {
      try {
        HOST_NAME = InetAddress.getLocalHost().getCanonicalHostName();
      } catch (UnknownHostException e) {
        log.error("Could not retrieve host name", e);
      }
    }

    return HOST_NAME;
  }
}
