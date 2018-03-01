/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * A repository for object data store
 *
 * @param <T> the object type stored in the repository
 */
public interface Repository<T> {
  boolean exists() throws Exception;

  void create(T value) throws Exception;

  void set(T value) throws Exception;

  void update(T value, DataUpdater<T> updater) throws Exception;

  T get() throws Exception;

  interface DataUpdater<T> {
    T apply(T currentValue, T newValue);
  }
}
