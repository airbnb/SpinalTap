/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.exception;

/** Exception thrown when a MySQL column value cannot be deserialized. */
public final class ColumnDeserializationException extends EntityDeserializationException {
  private static final long serialVersionUID = 935990977706712032L;

  private static String createMessage(String columnName, String tableName) {
    return String.format(
        "Failed to deserialize MySQL column %s in table %s", columnName, tableName);
  }

  public ColumnDeserializationException(
      final String columnName, final String tableName, final Throwable cause) {
    super(createMessage(columnName, tableName), cause);
  }
}
