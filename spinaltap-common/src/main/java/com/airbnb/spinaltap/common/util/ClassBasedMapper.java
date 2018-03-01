/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/** Maps an object according to the registered mapper for the class type */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ClassBasedMapper<T, R> implements Mapper<T, R> {
  private final Map<Class<? extends T>, Mapper<T, ? extends R>> locator;

  @SuppressWarnings("unchecked")
  public R map(T object) {
    Mapper<T, ? extends R> mapper = locator.get(object.getClass());

    if (mapper == null) {
      throw new UnsupportedOperationException("No mapper found for type " + object.getClass());
    }

    return mapper.map(object);
  }

  @NoArgsConstructor
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
