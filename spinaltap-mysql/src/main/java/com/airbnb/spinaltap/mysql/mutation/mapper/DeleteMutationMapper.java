/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.mapper;

import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.jitney.event.spinaltap.v1.MutationType;
import com.airbnb.spinaltap.mysql.mutation.MysqlDeleteMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import lombok.NonNull;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that maps a {@link
 * MysqlDeleteMutation} to its corresponding thrift {@link Mutation} form.
 */
class DeleteMutationMapper extends ThriftMutationMapper<MysqlDeleteMutation> {
  public DeleteMutationMapper(final String sourceId) {
    super(sourceId);
  }

  public Mutation map(@NonNull final MysqlDeleteMutation mutation) {
    final MysqlMutationMetadata metadata = mutation.getMetadata();

    return new Mutation(
        MutationType.DELETE,
        metadata.getTimestamp(),
        sourceId,
        metadata.getDataSource().getThriftDataSource(),
        createBinlogHeader(metadata, mutation.getType().getCode()),
        metadata.getTable().getThriftTable(),
        transformToEntity(mutation.getEntity()));
  }
}
