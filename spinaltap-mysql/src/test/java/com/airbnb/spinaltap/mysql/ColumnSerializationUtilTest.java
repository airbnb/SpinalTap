/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.assertEquals;

import com.airbnb.jitney.event.spinaltap.v1.BinlogHeader;
import com.airbnb.jitney.event.spinaltap.v1.DataSource;
import com.airbnb.jitney.event.spinaltap.v1.Mutation;
import com.airbnb.jitney.event.spinaltap.v1.MutationType;
import com.airbnb.jitney.event.spinaltap.v1.Table;
import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnDataType;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.Test;

public class ColumnSerializationUtilTest {
  private static final long TIMESTAMP = 1234;
  private static final String SOURCE_ID = "localhost";
  private static final BinlogHeader BINLOG_HEADER = new BinlogHeader("123", 2, 3, 4);
  private static final DataSource DATA_SOURCE = new DataSource("localhost", 9192, "db");
  private static final Table TABLE =
      new Table(
          TIMESTAMP,
          "table",
          "db",
          ImmutableSet.of("c1", "c2"),
          ImmutableMap.of("c1", new com.airbnb.jitney.event.spinaltap.v1.Column(1, false, "c1")));

  @Test
  public void testDeserializeColumn() throws Exception {
    Mutation mutation =
        new Mutation(
            MutationType.DELETE,
            TIMESTAMP,
            SOURCE_ID,
            DATA_SOURCE,
            BINLOG_HEADER,
            TABLE,
            getEntity());

    TSerializer serializer = new TSerializer(new TBinaryProtocol.Factory());
    TDeserializer deserializer = new TDeserializer(new TBinaryProtocol.Factory());

    byte[] serialized = serializer.serialize(mutation);

    Mutation deserialized = new Mutation();
    deserializer.deserialize(deserialized, serialized);

    assertEquals(mutation, deserialized);
  }

  private static Map<String, ByteBuffer> getEntity() {
    return ImmutableMap.of(
        "c1",
        ByteBuffer.wrap(
            ColumnSerializationUtil.serializeColumn(
                new Column(new ColumnMetadata("c1", ColumnDataType.INT24, false, 0, ""), 12345))),
        "c2",
        ByteBuffer.wrap(
            ColumnSerializationUtil.serializeColumn(
                new Column(new ColumnMetadata("c2", ColumnDataType.STRING, false, 1, ""), "string"))),
        "c3",
        ByteBuffer.wrap(
            ColumnSerializationUtil.serializeColumn(
                new Column(
                    new ColumnMetadata("c3", ColumnDataType.BLOB, false, 2, ""),
                    "blob.data".getBytes()))),
        "c4",
        ByteBuffer.wrap(
            ColumnSerializationUtil.serializeColumn(
                new Column(new ColumnMetadata("c4", ColumnDataType.DATETIME, false, 3, ""), null))));
  }
}
