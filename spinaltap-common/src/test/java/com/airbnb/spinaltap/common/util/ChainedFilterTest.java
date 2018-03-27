/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChainedFilterTest {
  @Test
  public void testFailingFilter() throws Exception {
    Filter<Integer> filter =
        ChainedFilter.<Integer>builder()
            .addFilter(num -> true)
            .addFilter(num -> false)
            .build();

    assertFalse(filter.apply(1));
  }
  @Test
  public void testPassingFilter() throws Exception {
    Filter<Integer> filter =
        ChainedFilter.<Integer>builder()
            .addFilter(num -> true)
            .addFilter(num -> true)
            .build();

    assertTrue(filter.apply(1));
  }

  @Test
  public void testEmptyFilter() throws Exception {
    Filter<Integer> filter = ChainedFilter.<Integer>builder().build();

    assertTrue(filter.apply(1));
  }
}
