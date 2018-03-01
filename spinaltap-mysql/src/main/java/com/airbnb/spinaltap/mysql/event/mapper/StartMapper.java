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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

@Slf4j
@RequiredArgsConstructor
class StartMapper implements Mapper<StartEvent, List<MysqlMutation>> {
  private final DataSource dataSource;
  private final TableCache tableCache;
  private final MysqlSourceMetrics metrics;

  public List<MysqlMutation> map(StartEvent event) {
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
