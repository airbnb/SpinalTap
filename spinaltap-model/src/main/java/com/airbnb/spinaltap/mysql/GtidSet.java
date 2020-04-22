/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/** This is an improvement of com.github.shyiko.mysql.binlog.GtidSet */
@EqualsAndHashCode
public class GtidSet {
  private static final Splitter COMMA_SPLITTER = Splitter.on(',');
  private static final Splitter COLUMN_SPLITTER = Splitter.on(':');
  private static final Splitter DASH_SPLITTER = Splitter.on('-');
  private static final Joiner COMMA_JOINER = Joiner.on(',');
  private static final Joiner COLUMN_JOINER = Joiner.on(':');

  // Use sorted map here so we can have a consistent GTID representation
  private final Map<String, UUIDSet> map = new TreeMap<>();

  public GtidSet(String gtidSetString) {
    if (Strings.isNullOrEmpty(gtidSetString)) {
      return;
    }
    gtidSetString = gtidSetString.replaceAll("\n", "").replaceAll("\r", "");
    for (String uuidSet : COMMA_SPLITTER.split(gtidSetString)) {
      Iterator<String> uuidSetIter = COLUMN_SPLITTER.split(uuidSet).iterator();
      if (uuidSetIter.hasNext()) {
        String uuid = uuidSetIter.next();
        List<Interval> intervals = new LinkedList<>();
        while (uuidSetIter.hasNext()) {
          Iterator<String> intervalIter = DASH_SPLITTER.split(uuidSetIter.next()).iterator();
          if (intervalIter.hasNext()) {
            long start = Long.parseLong(intervalIter.next());
            long end = intervalIter.hasNext() ? Long.parseLong(intervalIter.next()) : start;
            intervals.add(new Interval(start, end));
          }
        }
        if (intervals.size() > 0) {
          map.put(uuid, new UUIDSet(uuid, intervals));
        }
      }
    }
  }

  public boolean isContainedWithin(GtidSet other) {
    if (other == null) {
      return false;
    }
    if (this.equals(other)) {
      return true;
    }

    for (UUIDSet uuidSet : map.values()) {
      UUIDSet thatSet = other.map.get(uuidSet.getUuid());
      if (!uuidSet.isContainedWithin(thatSet)) {
        return false;
      }
    }
    return true;
  }

  @Override
  @JsonValue
  public String toString() {
    return COMMA_JOINER.join(map.values());
  }

  @Getter
  @EqualsAndHashCode
  public static final class UUIDSet {
    private final String uuid;
    private final List<Interval> intervals;

    public UUIDSet(String uuid, List<Interval> intervals) {
      this.uuid = uuid.toLowerCase();
      this.intervals = intervals;

      // Collapse intervals if possible
      Collections.sort(this.intervals);
      for (int i = intervals.size() - 1; i > 0; i--) {
        Interval before = this.intervals.get(i - 1);
        Interval after = this.intervals.get(i);
        if (after.getStart() <= before.getEnd() + 1) {
          if (after.getEnd() > before.getEnd()) {
            this.intervals.set(i - 1, new Interval(before.getStart(), after.getEnd()));
          }
          this.intervals.remove(i);
        }
      }
    }

    public boolean isContainedWithin(UUIDSet other) {
      if (other == null) {
        return false;
      }
      if (!this.uuid.equalsIgnoreCase(other.uuid)) {
        return false;
      }
      if (this.intervals.isEmpty()) {
        return true;
      }
      if (other.intervals.isEmpty()) {
        return false;
      }

      // every interval in this must be within an interval of the other ...
      for (Interval thisInterval : this.intervals) {
        boolean found = false;
        for (Interval otherInterval : other.intervals) {
          if (thisInterval.isContainedWithin(otherInterval)) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false; // didn't find a match
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return uuid + ":" + COLUMN_JOINER.join(intervals);
    }
  }

  @Value
  public static class Interval implements Comparable<Interval> {
    long start, end;

    public boolean isContainedWithin(Interval other) {
      if (other == this) {
        return true;
      }
      if (other == null) {
        return false;
      }
      return this.start >= other.start && this.end <= other.end;
    }

    @Override
    public String toString() {
      return start + "-" + end;
    }

    @Override
    public int compareTo(Interval other) {
      if (this.start != other.start) {
        return Long.compare(this.start, other.start);
      }
      return Long.compare(this.end, other.end);
    }
  }
}
