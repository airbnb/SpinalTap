/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.mapper;

import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.jitney.event.spinaltap.v1.MutationType;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.MysqlUpdateMutation;
import lombok.NonNull;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that maps a {@link
 * MysqlUpdateMutation} to its corresponding thrift {@link Mutation} form.
 */
class UpdateMutationMapper extends ThriftMutationMapper<MysqlUpdateMutation> {
  public UpdateMutationMapper(final String sourceId) {
    super(sourceId);
  }

  public Mutation map(@NonNull final MysqlUpdateMutation mutation) {
    final MysqlMutationMetadata metadata = mutation.getMetadata();

    final Mutation thriftMutation =
        new Mutation(
            MutationType.UPDATE,
            metadata.getTimestamp(),
            sourceId,
            metadata.getDataSource().getThriftDataSource(),
            createBinlogHeader(metadata, mutation.getType().getCode()),
            metadata.getTable().getThriftTable(),
            transformToEntity(mutation.getRow()));

    thriftMutation.setPreviousEntity(transformToEntity(mutation.getPreviousRow()));
    return thriftMutation;
  }
}
