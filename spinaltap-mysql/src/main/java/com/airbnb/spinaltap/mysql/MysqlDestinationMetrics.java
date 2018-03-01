/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.destination.DestinationMetrics;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;

public class MysqlDestinationMetrics extends DestinationMetrics {
  public MysqlDestinationMetrics(String sourceName, TaggedMetricRegistry metricRegistry) {
    this("mysql", sourceName, metricRegistry);
  }

  protected MysqlDestinationMetrics(
      String sourceType, String sourceName, TaggedMetricRegistry metricRegistry) {
    super(sourceName, sourceType, metricRegistry);
  }

  @Override
  protected Map<String, String> getTags(Mutation.Metadata meta) {
    Preconditions.checkState(meta instanceof MysqlMutationMetadata);
    MysqlMutationMetadata metadata = (MysqlMutationMetadata) meta;

    Map<String, String> metadataTags = new HashMap<>();

    metadataTags.put("database_name", metadata.getTable().getDatabase());
    metadataTags.put("table_name", metadata.getTable().getName());
    metadataTags.putAll(super.getTags(meta));

    return metadataTags;
  }
}
