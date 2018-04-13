/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClassBasedMapperTest {
  @Test
  public void testMap() throws Exception {
    Mapper<Object, Integer> mapper =
        ClassBasedMapper.<Object, Integer>builder()
            .addMapper(Float.class, Math::round)
            .addMapper(String.class, Integer::parseInt)
            .build();

    assertEquals(new Integer(1), mapper.map(new Float(1.2)));
    assertEquals(new Integer(3), mapper.map("3"));
  }

  @Test(expected = IllegalStateException.class)
  public void testNoMapFound() throws Exception {
    ClassBasedMapper.<Object, Integer>builder().build().map(1);
  }
}
