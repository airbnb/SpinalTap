/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** Filters events based on the db/table they belong to */
@RequiredArgsConstructor
class TableFilter extends MysqlEventFilter {
  private final TableCache tableCache;
  private final Set<String> tableNames;

  public boolean apply(BinlogEvent event) {
    if (event instanceof TableMapEvent) {
      TableMapEvent tableMap = (TableMapEvent) event;
      return tableNames.contains(
          Table.canonicalNameOf(tableMap.getDatabase(), tableMap.getTable()));
    } else if (event.isMutation()) {
      return tableCache.contains(event.getTableId());
    }

    return true;
  }
}
