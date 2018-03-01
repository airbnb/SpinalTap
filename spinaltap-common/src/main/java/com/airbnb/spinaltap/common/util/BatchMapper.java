/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import java.util.List;

/**
 * Responsible for mapping a list of objects
 *
 * @param <T> The mapped from object type
 * @param <R> The mapped to object type
 */
public interface BatchMapper<T, R> {
  List<R> apply(List<T> objects);
}
