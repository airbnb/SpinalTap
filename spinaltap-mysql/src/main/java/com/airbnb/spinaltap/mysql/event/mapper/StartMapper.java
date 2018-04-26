/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that keeps track of binlog file
 * starts detected on {@link StartEvent}s. This is used to clear the {@link TableCache}, to ensure
 * table to tableId mapping remains consistent.
 */
@Slf4j
@RequiredArgsConstructor
final class StartMapper implements Mapper<StartEvent, List<MysqlMutation>> {
  @NonNull private final DataSource dataSource;
  @NonNull private final TableCache tableCache;
  @NonNull private final MysqlSourceMetrics metrics;

  public List<MysqlMutation> map(@NonNull final StartEvent event) {
    log.info(
        "Started processing binlog file {} for host {} at {}",
        event.getBinlogFilePos().getFileName(),
        dataSource.getHost(),
        new DateTime(event.getTimestamp()));

    metrics.binlogFileStart();

    tableCache.clear();
    return Collections.emptyList();
  }
}
