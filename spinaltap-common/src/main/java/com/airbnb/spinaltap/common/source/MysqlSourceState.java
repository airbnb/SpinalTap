/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Represents the state of a {@link Source}, based on the last {@link SourceEvent} streamed. This is
 * used to mark the checkpoint for the {@link Source}, which will help indicate what position to
 * point to in the changelog on restart.
 *
 * <p>At the moment, the implement is coupled to binlog event state and therefore confined to {@code
 * MysqlSource} usage.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MysqlSourceState extends SourceState {
  /** The timestamp of the last streamed {@link SourceEvent} in the changelog. */
  @JsonProperty private long lastTimestamp;

  /** The offset of the last streamed {@link SourceEvent} in the changelog. */
  @JsonProperty private long lastOffset;

  /** The {@link BinlogFilePos} of the last streamed {@link SourceEvent} in the changelog. */
  @JsonProperty private BinlogFilePos lastPosition;

  public MysqlSourceState(
      final long lastTimestamp,
      final long lastOffset,
      final long currentLeaderEpoch,
      final BinlogFilePos lastPosition) {
    super(currentLeaderEpoch);
    this.lastTimestamp = lastTimestamp;
    this.lastOffset = lastOffset;
    this.lastPosition = lastPosition;
  }
}
