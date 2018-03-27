/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClassBasedMapperTest {
  private final Mapper<Float, Integer> floatMapper =
      new Mapper<Float, Integer>() {
        public Integer map(Float num) {
          return Math.round(num);
        }
      };

  private final Mapper<String, Integer> stringMapper =
      new Mapper<String, Integer>() {
        public Integer map(String num) {
          return Integer.parseInt(num);
        }
      };

  @Test
  public void testMap() throws Exception {
    Mapper<Object, Integer> mapper =
        ClassBasedMapper.<Object, Integer>builder()
            .addMapper(Float.class, floatMapper)
            .addMapper(String.class, stringMapper)
            .build();

    assertEquals(new Integer(1), mapper.map(new Float(1.2)));
    assertEquals(new Integer(3), mapper.map("3"));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testNoMapFound() throws Exception {
    Mapper<Object, Integer> mapper = ClassBasedMapper.<Object, Integer>builder().build();

    mapper.map(1);
  }
}
