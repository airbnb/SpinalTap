/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Filter} for {@link BinlogEvent}s based on
 * the database table they belong to. This is used to ensure that mutations are propagated only for
 * events for the table the {@link com.airbnb.spinaltap.common.source.Source} is subscribed to.
 */
@RequiredArgsConstructor
final class TableFilter extends MysqlEventFilter {
  @NonNull private final TableCache tableCache;
  @NonNull private final Set<String> tableNames;

  public boolean apply(@NonNull final BinlogEvent event) {
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
