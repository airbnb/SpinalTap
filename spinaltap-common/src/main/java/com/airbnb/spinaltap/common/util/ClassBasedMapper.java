/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Maps an object according to the registered mapper by {@link Class} type. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ClassBasedMapper<T, R> implements Mapper<T, R> {
  @NonNull private final Map<Class<? extends T>, Mapper<T, ? extends R>> locator;

  public static <T, R> ClassBasedMapper.Builder<T, R> builder() {
    return new ClassBasedMapper.Builder<>();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public R map(@NonNull final T object) {
    Mapper<T, ? extends R> mapper = locator.get(object.getClass());
    Preconditions.checkState(mapper != null, "No mapper found for type " + object.getClass());

    return mapper.map(object);
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class Builder<T, R> {
    private final Map<Class<? extends T>, Mapper<? extends T, ? extends R>> locator =
        new HashMap<>();

    public <S extends T> Builder<T, R> addMapper(Class<S> klass, Mapper<S, ? extends R> mapper) {
      locator.put(klass, mapper);
      return this;
    }

    @SuppressWarnings("unchecked")
    public Mapper<T, R> build() {
      return new ClassBasedMapper(locator);
    }
  }
}
