/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.mapper;

import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.mysql.event.GTIDEvent;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Mapper} that keeps track of {@link
 * GTIDEvent}s, which will be included in {@link com.airbnb.spinaltap.mysql.Transaction}
 */
@RequiredArgsConstructor
final class GTIDMapper implements Mapper<GTIDEvent, List<MysqlMutation>> {
  private final AtomicReference<String> gtid;

  @Override
  public List<MysqlMutation> map(@NonNull final GTIDEvent event) {
    gtid.set(event.getGtid());
    return Collections.emptyList();
  }
}
