/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.zookeeper.server.ByteBufferInputStream;

@Slf4j
public class ColumnSerializationUtil {
  public static byte[] serializeColumn(Column oldColumn) {
    return SerializationUtils.serialize(oldColumn.getValue());
  }

  /**
   * mapping between column type to java type BIT => BitSet ENUM, YEAR TINY, SHORT, INT24, LONG =>
   * int SET, LONGLONG => long FLOAT => float DOUBLE => value NEWDECIMAL => BigDecimal DATE => Date
   * TIME, TIME_V2 => Time TIMESTAMP, TIMESTAMP_V2 => Timestmap DATETIME, DATETIME_V2 => Date case
   * YEAR: STRING, VARCHAR, VAR_STRING => String BLOB => byte[]
   */
  public static Serializable deserializeColumn(Map<String, ByteBuffer> entity, String col) {
    ByteBuffer byteBuffer = entity.get(col);

    if (byteBuffer == null) {
      return null;
    }

    ByteBufferInputStream s = new ByteBufferInputStream(byteBuffer);
    return (Serializable) SerializationUtils.deserialize(s);
  }

  // TODO(kai.liu) airbnb specific deserialization.
}
