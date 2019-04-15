/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import java.util.List;

/**
 * Responsible for mapping a list of objects.
 *
 * @param <T> The mapped from object type.
 * @param <R> The mapped to object type.
 */
@FunctionalInterface
public interface BatchMapper<T, R> {
  /**
   * Applies the mapping function on the list of objects.
   *
   * @param objects the objects to map.
   * @return the mapped objects.
   */
  List<R> apply(List<T> objects);
}
