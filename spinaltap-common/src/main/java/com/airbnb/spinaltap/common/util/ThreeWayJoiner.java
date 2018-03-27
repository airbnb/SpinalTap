/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import javax.annotation.Nullable;

/**
 * Joins three objects to produce a fourth.
 *
 * @param <X> The first object type.
 * @param <Y> The second object type.
 * @param <Z> The third object type.
 * @param <R> The result object type.
 */
@FunctionalInterface
public interface ThreeWayJoiner<X, Y, Z, R> {
  /**
   * Applies the joiner on a triplet of objects.
   *
   * @param first the first object to join.
   * @param second the second object to join.
   * @param third the third object to join.
   * @return the resulting joined object.
   */
  @Nullable
  R apply(@Nullable X first, @Nullable Y second, @Nullable Z third);
}
