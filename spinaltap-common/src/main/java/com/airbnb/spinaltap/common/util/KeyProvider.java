/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * Responsible for providing a key for an object.
 *
 * @param <T> The object type.
 * @param <T> The key type.
 */
@FunctionalInterface
public interface KeyProvider<T, R> {
  /**
   * Gets the key for an object.
   *
   * @param object the object to get the key for.
   * @return the resulting key.
   */
  R get(T object);
}
