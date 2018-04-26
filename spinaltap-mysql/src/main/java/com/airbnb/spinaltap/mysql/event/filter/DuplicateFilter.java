/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Filter} for duplicate {@link BinlogEvent}s
 * that have already been streamed. This is determined by comparing against the offset of the last
 * marked {@link SourceState} checkpoint
 */
@RequiredArgsConstructor
public final class DuplicateFilter extends MysqlEventFilter {
  @NonNull private final AtomicReference<SourceState> state;

  public boolean apply(@NonNull final BinlogEvent event) {
    return !event.isMutation() || event.getOffset() > state.get().getLastOffset();
  }
}
