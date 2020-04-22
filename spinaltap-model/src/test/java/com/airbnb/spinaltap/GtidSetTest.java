/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.airbnb.spinaltap.mysql.GtidSet;
import org.junit.Test;

public class GtidSetTest {
  private static final String SERVER_UUID_1 = "665ef2f4-b008-4440-b78c-26ba7ce500e6";
  private static final String SERVER_UUID_2 = "eeb24231-ff9d-4051-b9b1-bf40bf33b2be";

  @Test
  public void testEmptySet() {
    assertEquals(new GtidSet("").toString(), "");
  }

  @Test
  public void testEquals() {
    assertEquals(new GtidSet(""), new GtidSet(""));
    assertEquals(new GtidSet(""), new GtidSet(null));

    assertEquals(new GtidSet(SERVER_UUID_1 + ":1-888"), new GtidSet(SERVER_UUID_1 + ":1-888"));

    GtidSet gtidSet1 =
        new GtidSet(String.format("%s:1-1023,%s:1-888", SERVER_UUID_1, SERVER_UUID_2));
    GtidSet gtidSet2 =
        new GtidSet(String.format("%s:1-888,%s:1-1023", SERVER_UUID_2, SERVER_UUID_1));
    assertEquals(gtidSet1, gtidSet2);
    assertEquals(gtidSet1.toString(), gtidSet2.toString());

    assertNotEquals(
        new GtidSet(SERVER_UUID_1 + ":1-888"), new GtidSet(SERVER_UUID_1 + ":1-100:102-888"));
    assertNotEquals(new GtidSet(SERVER_UUID_1 + ":1-888"), new GtidSet(SERVER_UUID_2 + ":1-888"));
  }

  @Test
  public void testCollapseIntervals() {
    GtidSet gtidSet = new GtidSet(SERVER_UUID_1 + ":1-123:124:125-200");
    assertEquals(gtidSet, new GtidSet(SERVER_UUID_1 + ":1-200"));
    assertEquals(gtidSet.toString(), SERVER_UUID_1 + ":1-200");

    gtidSet = new GtidSet(SERVER_UUID_1 + ":1-201:202-211:239-244:245-300:400-409");
    assertEquals(gtidSet, new GtidSet(SERVER_UUID_1 + ":1-211:239-300:400-409"));
    assertEquals(gtidSet.toString(), SERVER_UUID_1 + ":1-211:239-300:400-409");

    gtidSet = new GtidSet(SERVER_UUID_1 + ":1-200:100-123:40-255:40-100:60-100:280-290:270-279");
    assertEquals(gtidSet.toString(), SERVER_UUID_1 + ":1-255:270-290");
  }

  @Test
  public void testSubsetOf() {
    GtidSet[] set = {
      new GtidSet(""),
      new GtidSet(SERVER_UUID_1 + ":1-191"),
      new GtidSet(SERVER_UUID_1 + ":192-199"),
      new GtidSet(SERVER_UUID_1 + ":1-191:192-199"),
      new GtidSet(SERVER_UUID_1 + ":1-191:193-199"),
      new GtidSet(SERVER_UUID_1 + ":2-199"),
      new GtidSet(SERVER_UUID_1 + ":1-200")
    };
    byte[][] subsetMatrix = {
      {1, 1, 1, 1, 1, 1, 1},
      {0, 1, 0, 1, 1, 0, 1},
      {0, 0, 1, 1, 0, 1, 1},
      {0, 0, 0, 1, 0, 0, 1},
      {0, 0, 0, 1, 1, 0, 1},
      {0, 0, 0, 1, 0, 1, 1},
      {0, 0, 0, 0, 0, 0, 1},
    };
    for (int i = 0; i < subsetMatrix.length; i++) {
      byte[] subset = subsetMatrix[i];
      for (int j = 0; j < subset.length; j++) {
        assertEquals(set[i].isContainedWithin(set[j]), subset[j] == 1);
      }
    }
  }
}
