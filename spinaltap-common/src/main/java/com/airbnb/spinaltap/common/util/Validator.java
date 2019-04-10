/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * Responsible for validation logic on an object.
 *
 * @param <T> The object type.
 */
public interface Validator<T> {
  /**
   * Validates the object.
   *
   * @param object the object
   */
  void validate(T object);

  /** Resets the state of the validator */
  void reset();
}
