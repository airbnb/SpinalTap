/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation.schema;

import java.util.HashMap;
import java.util.Map;

public enum ColumnDataType {
  DECIMAL(0),
  TINY(1),
  SHORT(2),
  LONG(3),
  FLOAT(4),
  DOUBLE(5),
  NULL(6),
  TIMESTAMP(7),
  LONGLONG(8),
  INT24(9),
  DATE(10),
  TIME(11),
  DATETIME(12),
  YEAR(13),
  NEWDATE(14),
  VARCHAR(15),
  BIT(16),
  // (TIMESTAMP|DATETIME|TIME)_V2 data types appeared in MySQL 5.6.4
  // @see http://dev.mysql.com/doc/internals/en/date-and-time-data-type-representation.html
  TIMESTAMP_V2(17),
  DATETIME_V2(18),
  TIME_V2(19),
  NEWDECIMAL(246),
  ENUM(247),
  SET(248),
  TINY_BLOB(249),
  MEDIUM_BLOB(250),
  LONG_BLOB(251),
  BLOB(252),
  VAR_STRING(253),
  STRING(254),
  GEOMETRY(255),
  /*
  todo: remove UNKNOWN
  This is known finite list of types. Any other value is not valid.
  Treating this case as an error (throwing exception etc) is better.
   */
  UNKNOWN(-1);

  private final int code;

  ColumnDataType(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  private static final Map<Integer, ColumnDataType> INDEX_BY_CODE;

  static {
    INDEX_BY_CODE = new HashMap<>();
    for (ColumnDataType columnType : values()) {
      INDEX_BY_CODE.put(columnType.code, columnType);
    }
  }

  /**
   * The Java type is an 8-bit signed two's complement integer. But MySql byte is not. So when
   * looking up the column type from a MySql byte, it must be upcast to an int first.
   *
   * <p>As an example, let's take BLOB/252. Mysql column type will be the byte 0b11111100. If casted
   * to a java integer it will be interpreted as -4.
   *
   * <p>Integer.toBinaryString((int)((byte) 252)): '11111111111111111111111111111100'
   */
  public static ColumnDataType byCode(byte code) {
    final int upcastedIntValue = code & 0xFF;
    return byCode(upcastedIntValue);
  }

  public static ColumnDataType byCode(int code) {
    if (INDEX_BY_CODE.containsKey(code)) {
      return INDEX_BY_CODE.get(code);
    }
    return UNKNOWN;
  }
}
