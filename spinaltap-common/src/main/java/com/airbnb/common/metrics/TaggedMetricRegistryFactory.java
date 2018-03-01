/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * This provides an easy way to get the TaggedMetricRegistry in any class. The initialize must be
 * called before getting the TaggedMetricRegistry, otherwise the metrics will be lost.
 *
 * <p>The TaggedMetricRegistry is also available for injection. Consider injecting it directly in
 * case you want metrics during object initialization or as a general dependency injection best
 * practice.
 */
public class TaggedMetricRegistryFactory {

  private static volatile TaggedMetricRegistry registry =
      TaggedMetricRegistry.NON_INITIALIZED_TAGGED_METRIC_REGISTRY;

  private TaggedMetricRegistryFactory() {}

  public static void initialize(@NonNull TaggedMetricRegistry taggedMetricRegistry) {
    registry = taggedMetricRegistry;
  }

  public static TaggedMetricRegistry get() {
    if (registry == TaggedMetricRegistry.NON_INITIALIZED_TAGGED_METRIC_REGISTRY) {
      log.warn(
          "get() called before metrics is initialized. return NON_INITIALIZED_TAGGED_METRIC_REGISTRY.");
    }
    return registry;
  }
}
