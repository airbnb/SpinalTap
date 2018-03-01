/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.exception;

public class SpinaltapException extends RuntimeException {
  private static final long serialVersionUID = -8074916613284028245L;

  public SpinaltapException(String message) {
    super(message);
  }

  public SpinaltapException(String message, Throwable cause) {
    super(message, cause);
  }
}
