/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceState {
  @JsonProperty private long lastTimestamp;
  @JsonProperty private long lastOffset;
  @JsonProperty private long currentLeaderEpoch;
  @JsonProperty private BinlogFilePos lastPosition;
}
