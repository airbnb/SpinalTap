/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
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

public abstract class MysqlEventFilter implements Filter<BinlogEvent> {
  public static Filter<BinlogEvent> create(
      TableCache tableCache, Set<String> tableNames, AtomicReference<SourceState> state) {
    return new ChainedFilter.Builder<BinlogEvent>()
        .addFilter(new EventTypeFilter())
        .addFilter(new TableFilter(tableCache, tableNames))
        .addFilter(new DuplicateFilter(state))
        .build();
  }
}
