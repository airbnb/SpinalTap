/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.mapper;

import com.airbnb.jitney.event.spinaltap.v1.BinlogHeader;
import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.spinaltap.common.util.ClassBasedMapper;
import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.ColumnSerializationUtil;
import com.airbnb.spinaltap.mysql.mutation.MysqlDeleteMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlInsertMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutationMetadata;
import com.airbnb.spinaltap.mysql.mutation.MysqlUpdateMutation;
import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.google.common.collect.ImmutableMap;
import java.nio.ByteBuffer;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class ThriftMutationMapper<T extends MysqlMutation>
    implements Mapper<T, com.airbnb.jitney.event.spinaltap.v1.Mutation> {
  protected final String sourceId;

  public static Mapper<com.airbnb.spinaltap.Mutation<?>, Mutation> create(String sourceId) {
    return ClassBasedMapper.<com.airbnb.spinaltap.Mutation<?>, Mutation>builder()
        .addMapper(MysqlInsertMutation.class, new InsertMutationMapper(sourceId))
        .addMapper(MysqlUpdateMutation.class, new UpdateMutationMapper(sourceId))
        .addMapper(MysqlDeleteMutation.class, new DeleteMutationMapper(sourceId))
        .build();
  }

  protected static BinlogHeader createBinlogHeader(MysqlMutationMetadata metadata, byte typeCode) {
    BinlogHeader header =
        new BinlogHeader(
            metadata.getFilePos().toString(),
            metadata.getServerId(),
            metadata.getTimestamp(),
            typeCode);

    if (metadata.getLastTransaction() != null) {
      header.setLastTransactionPos(metadata.getLastTransaction().getPosition().toString());
      header.setLastTransactionTimestamp(metadata.getLastTransaction().getTimestamp());
    }

    header.setLeaderEpoch(metadata.getLeaderEpoch());
    header.setId(metadata.getId());
    header.setEventRowPosition(metadata.getEventRowPosition());

    return header;
  }

  protected static Map<String, ByteBuffer> transformToEntity(Row row) {
    ImmutableMap.Builder<String, ByteBuffer> builder = ImmutableMap.builder();
    for (Column column : row.getColumns().values()) {
      builder.put(
          column.getMetadata().getName(),
          ByteBuffer.wrap(ColumnSerializationUtil.serializeColumn(column)));
    }
    return builder.build();
  }
}
