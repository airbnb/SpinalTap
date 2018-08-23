/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Splitter;
import java.io.Serializable;
import java.util.Iterator;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Represents the position in a binlog file. */
@Slf4j
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class BinlogFilePos implements Comparable<BinlogFilePos>, Serializable {
  private static final long serialVersionUID = 1549638989059430876L;

  private static final Splitter SPLITTER = Splitter.on(':');
  private static final String NULL_VALUE = "null";
  public static final String DEFAULT_BINLOG_FILE_NAME = "mysql-bin-changelog";

  @JsonProperty private String fileName;
  @JsonProperty private long position;
  @JsonProperty private long nextPosition;

  public BinlogFilePos(long fileNumber) {
    this(fileNumber, 4L, 4L);
  }

  public BinlogFilePos(String fileName) {
    this(fileName, 4L, 4L);
  }

  public BinlogFilePos(long fileNumber, long position, long nextPosition) {
    this(String.format("%s.%06d", DEFAULT_BINLOG_FILE_NAME, fileNumber), position, nextPosition);
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
    return getFileNumber() != other.getFileNumber()
        ? Long.compare(getFileNumber(), other.getFileNumber())
        : Long.compare(getPosition(), other.getPosition());
  }
}
