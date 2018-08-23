/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import javax.annotation.Nullable;

/**
 * Joins two objects to produce a third.
 *
 * @param <S> The first object type.
 * @param <T> The second object type.
 * @param <R> The result object type.
 */
@FunctionalInterface
public interface Joiner<S, T, R> {
  /**
   * Applies the joiner on a pair of objects
   *
   * @param first the first object to join
   * @param second the second object to join
   * @return the resulting joined object
   */
  @Nullable
  R apply(@Nullable S first, @Nullable T second);
}
