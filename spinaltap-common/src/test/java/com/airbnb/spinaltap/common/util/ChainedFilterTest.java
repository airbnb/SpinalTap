/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class ChainedFilterTest {

  @Test
  public void testFilter() throws Exception {
    Filter<Integer> filter =
        new ChainedFilter.Builder<Integer>()
            .addFilter((num) -> true)
            .addFilter((num) -> false)
            .build();

    assertFalse(filter.apply(1));

    filter =
        new ChainedFilter.Builder<Integer>()
            .addFilter((num) -> true)
            .addFilter((num) -> true)
            .build();

    assertTrue(filter.apply(1));

    filter = new ChainedFilter.Builder<Integer>().build();

    assertTrue(filter.apply(1));
  }
}
