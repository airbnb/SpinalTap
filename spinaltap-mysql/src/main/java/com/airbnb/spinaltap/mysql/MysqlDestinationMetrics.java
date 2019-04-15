/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
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
import lombok.NonNull;

/**
 * Responsible for metrics collection on operations for {@link
 * com.airbnb.spinaltap.common.destination.Destination} and associated components for a given {@link
 * MysqlSource}.
 */
public class MysqlDestinationMetrics extends DestinationMetrics {
  private static final String DATABASE_NAME_TAG = "database_name";
  private static final String TABLE_NAME_TAG = "table_name";

  public MysqlDestinationMetrics(
      @NonNull final String sourceName, @NonNull final TaggedMetricRegistry metricRegistry) {
    this("mysql", sourceName, metricRegistry);
  }

  protected MysqlDestinationMetrics(
      @NonNull final String sourceType,
      @NonNull final String sourceName,
      @NonNull final TaggedMetricRegistry metricRegistry) {
    super(sourceName, sourceType, metricRegistry);
  }

  @Override
  protected Map<String, String> getTags(@NonNull final Mutation.Metadata metadata) {
    Preconditions.checkState(metadata instanceof MysqlMutationMetadata);

    MysqlMutationMetadata mysqlMetadata = (MysqlMutationMetadata) metadata;
    Map<String, String> metadataTags = new HashMap<>();

    metadataTags.put(DATABASE_NAME_TAG, mysqlMetadata.getTable().getDatabase());
    metadataTags.put(TABLE_NAME_TAG, mysqlMetadata.getTable().getName());
    metadataTags.putAll(super.getTags(mysqlMetadata));

    return metadataTags;
  }
}
