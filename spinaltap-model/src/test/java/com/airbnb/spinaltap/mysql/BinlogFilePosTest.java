/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.*;

import org.junit.Test;

public class BinlogFilePosTest {
  @Test
  public void testCompare() throws Exception {
    BinlogFilePos first = BinlogFilePos.fromString("mysql-bin-changelog.218:14:6");
    BinlogFilePos second = BinlogFilePos.fromString("mysql-bin-changelog.218:27:12");
    BinlogFilePos third = BinlogFilePos.fromString("mysql-bin-changelog.219:11:92");
    BinlogFilePos fourth = BinlogFilePos.fromString("mysql-bin-changelog.219:11:104");

    assertTrue(first.compareTo(second) < 0);
    assertTrue(third.compareTo(second) > 0);
    assertTrue(third.compareTo(fourth) == 0);
  }

  @Test
  public void testConstructor() throws Exception {
    assertEquals(
        BinlogFilePos.fromString("mysql-bin-changelog.000218:14:6"),
        new BinlogFilePos("mysql-bin-changelog.000218", 14, 6));

    assertEquals(new BinlogFilePos(80887L), new BinlogFilePos("mysql-bin-changelog.080887"));

    assertEquals(new BinlogFilePos(1080887L), new BinlogFilePos("mysql-bin-changelog.1080887"));
  }
}
