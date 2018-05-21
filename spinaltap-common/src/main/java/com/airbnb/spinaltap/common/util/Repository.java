/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

/**
 * Represents a single object data store repository.
 *
 * @param <T> the object type stored in the repository.
 */
public interface Repository<T> {
  /** @return whether an object exists */
  boolean exists() throws Exception;

  /**
   * Creates a new object.
   *
   * @param value the object value.
   */
  void create(T value) throws Exception;

  /**
   * Sets the current object value.
   *
   * @param value the object value.
   */
  void set(T value) throws Exception;

  /**
   * Updates the current object value given a {@link DataUpdater}.
   *
   * @param value the object value.
   * @param updater the updater .
   */
  void update(T value, DataUpdater<T> updater) throws Exception;

  /** Retrieves the current object value. */
  T get() throws Exception;

  /**
   * Responsible for determining the object value, as a function of the current value and new value.
   *
   * @param <T> the object value type.
   */
  interface DataUpdater<T> {
    T apply(T currentValue, T newValue);
  }
}
