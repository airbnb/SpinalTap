/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Base class which represents an entity change
 *
 * @param <T>
 */
@Getter
@ToString
@RequiredArgsConstructor
public abstract class Mutation<T> {
  private static final byte INSERT_BYTE = 0x1;
  private static final byte UPDATE_BYTE = 0x2;
  private static final byte DELETE_BYTE = 0x3;
  private static final byte INVALID_BYTE = 0x4;

  @Getter
  @RequiredArgsConstructor
  public enum Type {
    INSERT(INSERT_BYTE),
    UPDATE(UPDATE_BYTE),
    DELETE(DELETE_BYTE),
    INVALID(INVALID_BYTE);

    final byte code;

    public static Type fromCode(byte code) {
      switch (code) {
        case INSERT_BYTE:
          return INSERT;
        case UPDATE_BYTE:
          return UPDATE;
        case DELETE_BYTE:
          return DELETE;
        default:
          return INVALID;
      }
    }
  }

  private final Metadata metadata;
  private final Type type;
  private final T entity;

  @Getter
  @ToString
  @RequiredArgsConstructor
  public abstract static class Metadata {
    private final long id;
    private final long timestamp;
  }

  // For use by subclasses that implement a mutation with type UPDATE.
  protected static Set<String> getUpdatedColumns(
      Map<String, ?> previousValues, Map<String, ?> currentValues) {
    Set<String> previousColumns = previousValues.keySet();
    Set<String> currentColumns = currentValues.keySet();

    return ImmutableSet.<String>builder()
        .addAll(Sets.symmetricDifference(currentColumns, previousColumns))
        .addAll(
            Sets.intersection(currentColumns, previousColumns)
                .stream()
                .filter(
                    column ->
                        // Use deepEquals to allow testing for equality between two byte arrays.
                        !Objects.deepEquals(previousValues.get(column), currentValues.get(column)))
                .collect(Collectors.toSet()))
        .build();
  }
}
