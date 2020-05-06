/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.GTIDEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a {@link com.airbnb.spinaltap.common.util.Filter} for {@link BinlogEvent}s based on a
 * predefined whitelist of event class types.
 */
@RequiredArgsConstructor
final class EventTypeFilter extends MysqlEventFilter {
  @SuppressWarnings("unchecked")
  private static final Set<Class<? extends BinlogEvent>> WHITELISTED_EVENT_TYPES =
      ImmutableSet.of(
          TableMapEvent.class,
          WriteEvent.class,
          UpdateEvent.class,
          DeleteEvent.class,
          XidEvent.class,
          QueryEvent.class,
          StartEvent.class,
          GTIDEvent.class);

  public boolean apply(@NonNull final BinlogEvent event) {
    return WHITELISTED_EVENT_TYPES.contains(event.getClass());
  }
}
