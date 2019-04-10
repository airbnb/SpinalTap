/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.zookeeper.server.ByteBufferInputStream;

/** A utility class for MySQL {@link Column} SerDe supoort. */
@Slf4j
@UtilityClass
public class ColumnSerializationUtil {
  public static byte[] serializeColumn(@NonNull final Column oldColumn) {
    return SerializationUtils.serialize(oldColumn.getValue());
  }

  /**
   * mapping between column type to java type BIT => BitSet ENUM, YEAR TINY, SHORT, INT24, LONG =>
   * int SET, LONGLONG => long FLOAT => float DOUBLE => value NEWDECIMAL => BigDecimal DATE => Date
   * TIME, TIME_V2 => Time TIMESTAMP, TIMESTAMP_V2 => Timestmap DATETIME, DATETIME_V2 => Date case
   * YEAR: STRING, VARCHAR, VAR_STRING => String BLOB => byte[]
   */
  public static Serializable deserializeColumn(
      @NonNull final Map<String, ByteBuffer> entity, @NonNull final String column) {
    final ByteBuffer byteBuffer = entity.get(column);

    if (byteBuffer == null) {
      return null;
    }

    final ByteBufferInputStream inputStream = new ByteBufferInputStream(byteBuffer);
    return (Serializable) SerializationUtils.deserialize(inputStream);
  }
}
