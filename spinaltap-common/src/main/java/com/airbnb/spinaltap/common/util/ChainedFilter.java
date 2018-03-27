/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chain of {@link Filter}s, where all {@link Filter} conditions need to pass.
 *
 * @param <T> the filtered object type.
 */
@RequiredArgsConstructor
public class ChainedFilter<T> implements Filter<T> {
  @NonNull private final List<Filter<T>> filters;

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Applies the filters on the object.
   *
   * @param object the object to filter.
   * @return {@code true} if all filter conditions pass, {@code false} otherwise.
   */
  public boolean apply(final T object) {
    return filters.stream().allMatch(filter -> filter.apply(object));
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Builder<T> {
    private final List<Filter<T>> filters = new ArrayList<>();

    public Builder<T> addFilter(Filter<T> filter) {
      filters.add(filter);
      return this;
    }

    public Filter<T> build() {
      return new ChainedFilter<>(filters);
    }
  }
}
