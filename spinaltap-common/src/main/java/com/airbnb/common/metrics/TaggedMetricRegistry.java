/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.common.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import java.util.Map;

/**
 * The TaggedMetricRegistry is a proxy for a MetricRegistry that enables tags for Metrics. It relies
 * on the DatadogReporter to parse metrics of the form metric.name[tag1:value1,tag2:value2] into a
 * name metric.name and tags tag1:value1 and tag2:value2.
 *
 * <p>It proxies the create methods of the MetricRegistry directly. If you want to access the
 * retrieval methods of the MetricRegistry (such as getCounters()) you should call
 * getMetricRegistry() to get the underlying registry.
 *
 * <p>It uses composition instead of inheritance because Dropwizard statically initializes its
 * MetricRegistry and makes it difficult to subclass. And composition is fun.
 */
public class TaggedMetricRegistry {

  static final String UNTAGGED_SUFFIX = ".untagged";

  static final TaggedMetricRegistry NON_INITIALIZED_TAGGED_METRIC_REGISTRY =
      new TaggedMetricRegistry();

  private final MetricRegistry registry;

  public TaggedMetricRegistry() {
    this(new MetricRegistry());
  }

  public TaggedMetricRegistry(MetricRegistry registry) {
    this.registry = registry;
  }

  public static String name(String name, String... names) {
    return MetricRegistry.name(name, names);
  }

  public static String name(Class<?> klass, String... names) {
    return MetricRegistry.name(klass, names);
  }

  public <T extends Metric> T register(String name, T metric) {
    return registry.register(name, metric);
  }

  public <T extends Metric> T register(String name, Map<String, String> tags, T metric) {
    return registry.register(taggedName(name, tags), metric);
  }

  public <T extends Metric> T register(String name, T metric, String... tags) {
    return registry.register(taggedName(name, tags), metric);
  }

  public boolean remove(String name) {
    return registry.remove(name);
  }

  public boolean remove(String name, Map<String, String> tags) {
    return registry.remove(taggedName(name, tags));
  }

  public boolean remove(String name, String... tags) {
    return registry.remove(taggedName(name, tags));
  }

  /**
   * Build the tagged metric for Datadog from a map for tags in a key:value format.
   *
   * <p>We could validate that the name and tags don't contain [, ] or , because that might cause
   * problems if it's worth the performance impact.
   *
   * @param name the metric name
   * @param tags the associated tags from a key:value format
   */
  public static String taggedName(String name, Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return name;
    }
    return taggedName(name, getTagsAsArray(tags));
  }

  /**
   * Same as {@link #taggedName(String, Map)}, but takes variable number of tags in simple string
   * format.
   */
  public static String taggedName(String name, String... tags) {
    if (tags == null || tags.length < 1) {
      return name;
    }
    final StringBuilder builder = new StringBuilder();
    builder.append(name);
    builder.append("[");
    boolean first = true;
    for (String tag : tags) {
      if (!first) {
        builder.append(",");
      }
      builder.append(tag);
      first = false;
    }
    builder.append("]");
    return builder.toString();
  }

  public static String[] getTagsAsArray(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    // Can use java streams once the language level is upgraded
    String tagsArray[] = new String[tags.size()];
    int index = 0;
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      // Allocate the memory initially
      tagsArray[index++] =
          new StringBuilder(entry.getKey().length() + 1 + entry.getValue().length())
              .append(entry.getKey())
              .append(":")
              .append(entry.getValue())
              .toString();
    }
    return tagsArray;
  }

  public void registerAll(MetricSet metrics) {
    registry.registerAll(metrics);
  }

  public Counter counter(String name) {
    return new DropwizardCounter(registry.counter(name), registry.counter(name + UNTAGGED_SUFFIX));
  }

  public Counter counter(String name, Map<String, String> tags) {
    return new DropwizardCounter(
        registry.counter(taggedName(name, tags)), registry.counter(name + UNTAGGED_SUFFIX));
  }

  public Counter counter(String name, String... tags) {
    return new DropwizardCounter(
        registry.counter(taggedName(name, tags)), registry.counter(name + UNTAGGED_SUFFIX));
  }

  public Histogram histogram(String name) {
    return new DropwizardHistogram(registry.histogram(name), registry.histogram(name + UNTAGGED_SUFFIX));
  }

  public Histogram histogram(String name, Map<String, String> tags) {
    return new DropwizardHistogram(
        registry.histogram(taggedName(name, tags)), registry.histogram(name + UNTAGGED_SUFFIX));
  }

  public Histogram histogram(String name, String... tags) {
    return new DropwizardHistogram(
        registry.histogram(taggedName(name, tags)), registry.histogram(name + UNTAGGED_SUFFIX));
  }

  public MetricRegistry getMetricRegistry() {
    return registry;
  }
}
