/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;

/** Filters duplicate mutation events that have already been checkpointed */
@RequiredArgsConstructor
public class DuplicateFilter extends MysqlEventFilter {
  private final AtomicReference<SourceState> state;

  public boolean apply(BinlogEvent event) {
    return !event.isMutation() || event.getOffset() > state.get().getLastOffset();
  }
}
