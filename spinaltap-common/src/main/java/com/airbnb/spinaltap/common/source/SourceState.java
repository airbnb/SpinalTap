/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SourceState {
  /**
   * The leader epoch for the {@code Source}. The epoch acts as a high watermark, and is typically
   * incremented on leader election.
   *
   * <p>Note: This is only applicable if a cluster solution is employed. It is is used to mitigate
   * network partition (split brain) scenarios, and avoid having two cluster nodes concurrently
   * streaming from the same {@link Source}.
   */
  @JsonProperty private long currentLeaderEpoch;
}
