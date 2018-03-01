/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChainedFilter<T> implements Filter<T> {
  private final List<Filter<T>> filters;

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  public boolean apply(T object) {
    return filters.stream().allMatch(filter -> filter.apply(object));
  }

  public static class Builder<T> {
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
