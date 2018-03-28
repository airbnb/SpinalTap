/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import com.airbnb.spinaltap.common.exception.SpinaltapException;

/**
 * Responsible for handling {@code SpinaltapException}s.
 */
@FunctionalInterface
public interface ErrorHandler {
  void handle(SpinaltapException e) throws SpinaltapException;
}
