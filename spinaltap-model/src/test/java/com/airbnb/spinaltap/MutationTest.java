/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import org.junit.Test;

public class MutationTest {
  private static final String SHARED_KEY = "sharedKey";

  @Test
  public void testAddAndRemoveScalarColumns() {
    String removedKey = "removedKey";
    String addedKey = "addedKey";
    Map<String, String> previousColumns =
        ImmutableMap.of(
            removedKey, "removedValue",
            SHARED_KEY, "sharedValue");
    Map<String, String> currentColumns =
        ImmutableMap.of(
            SHARED_KEY, "sharedValue",
            addedKey, "addedValue");
    assertEquals(
        ImmutableSet.of(removedKey, addedKey),
        Mutation.getUpdatedColumns(previousColumns, currentColumns));
  }

  @Test
  public void testUpdateScalarColumnValues() {
    String updatedKey = "updatedKey";
    Map<String, String> previousColumns =
        ImmutableMap.of(
            SHARED_KEY, "sharedValue",
            updatedKey, "previousValue");
    Map<String, String> currentColumns =
        ImmutableMap.of(
            SHARED_KEY, "sharedValue",
            updatedKey, "currentValue");
    assertEquals(
        ImmutableSet.of(updatedKey), Mutation.getUpdatedColumns(previousColumns, currentColumns));
  }

  @Test
  public void testUpdateArrayColumnValues() {
    String updatedKey = "updatedKey";
    Map<String, Object> previousColumns =
        ImmutableMap.of(
            SHARED_KEY, new byte[] {0x00, 0x01},
            updatedKey, new byte[] {0x02, 0x03});
    Map<String, Object> currentColumns =
        ImmutableMap.of(
            SHARED_KEY, new byte[] {0x00, 0x01},
            updatedKey, new byte[] {0x04, 0x05});
    assertEquals(
        ImmutableSet.of(updatedKey), Mutation.getUpdatedColumns(previousColumns, currentColumns));
  }
}
