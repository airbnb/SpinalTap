/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.ChainedFilter;
import com.airbnb.spinaltap.common.util.Filter;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;

/** Base {@link com.airbnb.spinaltap.common.util.Filter} implement for MySQL {@link BinlogEvent}s */
public abstract class MysqlEventFilter implements Filter<BinlogEvent> {
  public static Filter<BinlogEvent> create(
      @NonNull final TableCache tableCache,
      @NonNull final Set<String> tableNames,
      @NonNull final AtomicReference<SourceState> state) {
    return ChainedFilter.<BinlogEvent>builder()
        .addFilter(new EventTypeFilter())
        .addFilter(new TableFilter(tableCache, tableNames))
        .addFilter(new DuplicateFilter(state))
        .build();
  }
}
