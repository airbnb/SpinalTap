/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.exception;

public class EntityDeserializationException extends SpinaltapException {
  private static final long serialVersionUID = 2604256281318886726L;

  public EntityDeserializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
