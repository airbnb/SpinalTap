/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.exception;

import com.airbnb.spinaltap.common.exception.SpinaltapException;

public class InvalidBinlogPositionException extends SpinaltapException {
  private static final long serialVersionUID = 9187451138457311547L;

  public InvalidBinlogPositionException(String message) {
    super(message);
  }
}
