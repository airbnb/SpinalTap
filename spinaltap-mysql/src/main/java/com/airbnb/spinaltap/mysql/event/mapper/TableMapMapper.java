/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that keeps track of {@link
 * com.airbnb.spinaltap.mysql.mutation.schema.Table} information from {@link TableMapEvent}s, which
 * will be appended as metadata to streamed {@link MysqlMutation}s.
 */
@Slf4j
@RequiredArgsConstructor
final class TableMapMapper implements Mapper<TableMapEvent, List<MysqlMutation>> {
  @NonNull private final TableCache tableCache;

  /**
   * Updates the {@link TableCache} with {@link com.airbnb.spinaltap.mysql.mutation.schema.Table}
   * information corresponding to the {@link TableMapEvent}. To maintain consistency, any errors
   * will be propagated if the cache update fails.
   */
  public List<MysqlMutation> map(@NonNull final TableMapEvent event) {
    try {
      tableCache.addOrUpdate(
          event.getTableId(),
          event.getTable(),
          event.getDatabase(),
          event.getBinlogFilePos(),
          event.getColumnTypes());
    } catch (Exception ex) {
      log.error("Failed to process table map event: " + event, ex);
      throw new RuntimeException(ex);
    }

    return Collections.emptyList();
  }
}
