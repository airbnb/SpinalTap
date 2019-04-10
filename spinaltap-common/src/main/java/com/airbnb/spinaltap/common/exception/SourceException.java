/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.exception;

public class SourceException extends SpinaltapException {
  private static final long serialVersionUID = -59599391802331914L;

  public SourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
