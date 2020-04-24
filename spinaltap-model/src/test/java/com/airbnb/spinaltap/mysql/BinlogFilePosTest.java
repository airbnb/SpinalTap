/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.*;

import org.junit.Test;

public class BinlogFilePosTest {
  private static final String UUID1 = "07592619-e257-4033-a30f-7fe9fcfbf229";
  private static final String UUID2 = "4a4ac150-fe5b-4093-a1ef-a8876011adaa";

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
  public void testCompareWithGTID() {
    String gtid1 = UUID1 + ":1-200";
    String gtid2 = UUID1 + ":1-300";
    String gtid3 = UUID1 + ":1-200," + UUID2 + ":1-456";
    BinlogFilePos first = new BinlogFilePos("mysql-bin-changelog.218", 123, 456, gtid1, UUID1);
    BinlogFilePos second = new BinlogFilePos("mysql-bin-changelog.218", 456, 789, gtid2, UUID1);
    BinlogFilePos third = new BinlogFilePos("mysql-bin-changelog.100", 10, 24, gtid1, UUID2);
    BinlogFilePos fourth = new BinlogFilePos("mysql-bin-changelog.100", 20, 24, gtid3, UUID2);

    // server_uuid matches, compare binlog file number and position
    assertTrue(first.compareTo(second) < 0);

    // server_uuid doesn't match, compare GTID
    assertEquals(0, first.compareTo(third));
    assertTrue(first.compareTo(fourth) < 0);
    assertTrue(second.compareTo(third) > 0);
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
