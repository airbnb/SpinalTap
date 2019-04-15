/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * Responsible for filtering object
 *
 * @param <T> The filtered object type
 */
@FunctionalInterface
public interface Filter<T> {
  /**
   * Applies the filter on the object
   *
   * @param object the object to filter
   * @return {@code true} if the filter condition passes, {@code false} otherwise
   */
  boolean apply(T object);
}
