/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Represents a base event streamed from a {@link Source}. */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public abstract class SourceEvent {
  private long timestamp = System.currentTimeMillis();

  /** Returns the number of entities in the event */
  public int size() {
    return 1;
  }
}
