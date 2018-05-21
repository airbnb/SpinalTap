/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * Responsible for mapping between objects.
 *
 * @param <T> The mapped from object type.
 * @param <R> The mapped to object type.
 */
@FunctionalInterface
public interface Mapper<T, R> {
  /**
   * Maps an object to another.
   *
   * @param object the object to map.
   * @return the resulting mapped object.
   */
  R map(T object);
}
