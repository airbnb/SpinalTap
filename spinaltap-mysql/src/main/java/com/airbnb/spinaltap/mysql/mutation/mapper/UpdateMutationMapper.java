/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.mapper;

import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.jitney.event.spinaltap.v1.MutationType;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.MysqlUpdateMutation;

class UpdateMutationMapper extends ThriftMutationMapper<MysqlUpdateMutation> {
  public UpdateMutationMapper(String sourceId) {
    super(sourceId);
  }

  public Mutation map(MysqlUpdateMutation mutation) {
    MysqlMutationMetadata metadata = mutation.getMetadata();

    Mutation thriftMutation =
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
