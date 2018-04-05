/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.google.common.base.Throwables;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class TableMapMapper implements Mapper<TableMapEvent, List<MysqlMutation>> {
  private final TableCache tableCache;

  public List<MysqlMutation> map(TableMapEvent event) {
    try {
      tableCache.addOrUpdate(
          event.getTableId(),
          event.getTable(),
          event.getDatabase(),
          event.getBinlogFilePos(),
          event.getColumnTypes());
    } catch (Exception ex) {
      log.error("Failed to process table map event: " + event, ex);
      Throwables.throwIfUnchecked(ex);
      throw new RuntimeException(ex);
    }

    return Collections.emptyList();
  }
}
