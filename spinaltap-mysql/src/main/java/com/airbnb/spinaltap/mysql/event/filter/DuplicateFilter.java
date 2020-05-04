/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.GtidSet;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Filter} for duplicate {@link BinlogEvent}s
 * that have already been streamed. This is used for server-side de-duplication, by comparing
 * against the offset of the last marked {@link SourceState} checkpoint and disregarding any events
 * that are received with an offset before that watermark.
 */
@RequiredArgsConstructor
public final class DuplicateFilter extends MysqlEventFilter {
  @NonNull private final AtomicReference<SourceState> state;

  public boolean apply(@NonNull final BinlogEvent event) {
    // Only applies to mutation events
    if (!event.isMutation()) {
      return true;
    }

    // We need to tell if position in `event` and in `state` are from the same source
    // MySQL server, because a failover may have happened and we are currently streaming
    // from the new master.
    // If they are from the same source server, we can just use the binlog filename and
    // position (offset) to tell whether we should skip this event.
    BinlogFilePos eventBinlogPos = event.getBinlogFilePos();
    BinlogFilePos savedBinlogPos = state.get().getLastPosition();
    // Use the same logic in BinlogFilePos.compareTo() here...
    if (BinlogFilePos.shouldCompareUsingFilePosition(eventBinlogPos, savedBinlogPos)) {
      return event.getOffset() > state.get().getLastOffset();
    }

    // If this point is reached, a master failover might have happened.
    // We can only use GTIDSet to tell whether this event should be skipped.
    // We should only skip this event if GTIDSet in event is a "proper subset" of the GTIDSet
    // in saved state, because it is possible that the last transaction we streamed before the
    // failover is in the middle of a transaction.
    GtidSet eventGtidSet = eventBinlogPos.getGtidSet();
    GtidSet savedGtidSet = savedBinlogPos.getGtidSet();
    return !eventGtidSet.isContainedWithin(savedGtidSet) && !eventGtidSet.equals(savedGtidSet);
  }
}
