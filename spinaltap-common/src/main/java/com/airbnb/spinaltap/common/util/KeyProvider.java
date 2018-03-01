/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * Responsible for providing a key for an object
 *
 * @param <T> The object type
 * @param <T> The key type
 */
public interface KeyProvider<T, R> {
  /**
   * Gets the key for an object
   *
   * @param object the object to get the key
   * @return the string key
   */
  R get(T object);
}
