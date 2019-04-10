/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.exception;

public class DestinationException extends SpinaltapException {
  private static final long serialVersionUID = -2160287795842968357L;

  public DestinationException(String message, Throwable cause) {
    super(message, cause);
  }
}
