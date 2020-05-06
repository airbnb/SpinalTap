/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Splitter;
import java.io.Serializable;
import java.util.Iterator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Represents the position in a binlog file. */
@Slf4j
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@JsonDeserialize(builder = BinlogFilePos.Builder.class)
public class BinlogFilePos implements Comparable<BinlogFilePos>, Serializable {
  private static final long serialVersionUID = 1549638989059430876L;

  private static final Splitter SPLITTER = Splitter.on(':');
  private static final String NULL_VALUE = "null";
  public static final String DEFAULT_BINLOG_FILE_NAME = "mysql-bin-changelog";

  @JsonProperty private String fileName;
  @JsonProperty private long position;
  @JsonProperty private long nextPosition;
  @JsonProperty private GtidSet gtidSet;
  @JsonProperty private String serverUUID;

  public BinlogFilePos(long fileNumber) {
    this(fileNumber, 4L, 4L);
  }

  public BinlogFilePos(String fileName) {
    this(fileName, 4L, 4L);
  }

  public BinlogFilePos(long fileNumber, long position, long nextPosition) {
    this(String.format("%s.%06d", DEFAULT_BINLOG_FILE_NAME, fileNumber), position, nextPosition);
  }

  public BinlogFilePos(
      String fileName, long position, long nextPosition, String gtidSet, String serverUUID) {
    this.fileName = fileName;
    this.position = position;
    this.nextPosition = nextPosition;
    this.serverUUID = serverUUID;
    if (gtidSet != null) {
      this.gtidSet = new GtidSet(gtidSet);
    }
  }

  public BinlogFilePos(String fileName, long position, long nextPosition) {
    this(fileName, position, nextPosition, null, null);
  }

  public static BinlogFilePos fromString(@NonNull final String position) {
    Iterator<String> parts = SPLITTER.split(position).iterator();
    String fileName = parts.next();
    String pos = parts.next();
    String nextPos = parts.next();

    if (NULL_VALUE.equals(fileName)) {
      fileName = null;
    }

    return new BinlogFilePos(fileName, Long.parseLong(pos), Long.parseLong(nextPos));
  }

  @JsonIgnore
  public long getFileNumber() {
    if (fileName == null) {
      return Long.MAX_VALUE;
    }
    if (fileName.equals("")) {
      return Long.MIN_VALUE;
    }
    String num = fileName.substring(fileName.lastIndexOf('.') + 1);
    return Long.parseLong(num);
  }

  @Override
  public String toString() {
    return String.format("%s:%d:%d", fileName, position, nextPosition);
  }

  @Override
  public int compareTo(@NonNull final BinlogFilePos other) {
    if (shouldCompareUsingFilePosition(this, other)) {
      return getFileNumber() != other.getFileNumber()
          ? Long.compare(getFileNumber(), other.getFileNumber())
          : Long.compare(getPosition(), other.getPosition());
    }

    if (this.gtidSet.equals(other.gtidSet)) {
      return 0;
    }
    if (this.gtidSet.isContainedWithin(other.gtidSet)) {
      return -1;
    }
    return 1;
  }

  /** Check if two BinlogFilePos are from the same source MySQL server */
  private static boolean isFromSameSource(BinlogFilePos pos1, BinlogFilePos pos2) {
    return pos1.getServerUUID() != null
        && pos1.getServerUUID().equalsIgnoreCase(pos2.getServerUUID());
  }

  /** Whether we can compare two BinlogFilePos using Binlog file position (without GTIDSet) */
  public static boolean shouldCompareUsingFilePosition(BinlogFilePos pos1, BinlogFilePos pos2) {
    return isFromSameSource(pos1, pos2) || pos1.getGtidSet() == null || pos2.getGtidSet() == null;
  }

  public static Builder builder() {
    return new Builder();
  }

  @JsonPOJOBuilder
  @NoArgsConstructor
  public static class Builder {
    private String fileName;
    private long position;
    private long nextPosition;
    private String gtidSet;
    private String serverUUID;

    public Builder withFileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder withPosition(long position) {
      this.position = position;
      return this;
    }

    public Builder withNextPosition(long nextPosition) {
      this.nextPosition = nextPosition;
      return this;
    }

    public Builder withGtidSet(String gtidSet) {
      this.gtidSet = gtidSet;
      return this;
    }

    public Builder withServerUUID(String serverUUID) {
      this.serverUUID = serverUUID;
      return this;
    }

    public BinlogFilePos build() {
      return new BinlogFilePos(fileName, position, nextPosition, gtidSet, serverUUID);
    }
  }
}
