/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.exception;

/** Exception thrown when a DynamoDB attribute value cannot be deserialized. */
public final class AttributeValueDeserializationException extends EntityDeserializationException {
  private static final long serialVersionUID = 2442564527939878665L;

  private static String createMessage(String attributeName, String tableName) {
    return String.format(
        "Could not deserialize thrift bytebuffer for DynamoDB attribute %s in table %s.",
        attributeName, tableName);
  }

  public AttributeValueDeserializationException(
      final String attributeName, final String tableName, final Throwable cause) {
    super(createMessage(attributeName, tableName), cause);
  }
}
