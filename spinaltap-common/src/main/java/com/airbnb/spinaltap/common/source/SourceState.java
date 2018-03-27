/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.mysql.BinlogFilePos;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the state of a {@link Source}, based on the last {@link SourceEvent} streamed. This is
 * used to mark the checkpoint for the {@link Source}, which will help indicate what position to
 * point to in the changelog on restart.
 *
 * <p>At the moment, the implement is coupled to binlog event state and therefore confined to
 * {@code MysqlSource} usage.</p>
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceState {
  /**
   * The timestamp of the last streamed {@link SourceEvent} in the changelog.
   */
  @JsonProperty private long lastTimestamp;

  /**
   * The offset of the last streamed {@link SourceEvent} in the changelog.
   */
  @JsonProperty private long lastOffset;

  /**
   * The leader epoch for the {@code Source}. The epoch acts as a high watermark, and is typically
   * incremented on leader election and/or {@code Source} restart.
   *
   * <p> Note: This is only applicable if a cluster solution is employed. It is is used to
   * mitigate network partition (split brain) scenarios, and avoid having two cluster nodes
   * concurrently streaming from the same {@link Source}. </p>
   */
  @JsonProperty private long currentLeaderEpoch;

  /**
   * The {@link BinlogFilePos} of the last streamed {@link SourceEvent} in the changelog.
   */
  @JsonProperty private BinlogFilePos lastPosition;
}
