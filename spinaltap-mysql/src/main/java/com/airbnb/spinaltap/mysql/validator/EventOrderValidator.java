/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.validator;

import com.airbnb.spinaltap.common.util.Validator;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EventOrderValidator implements Validator<BinlogEvent> {
  private final Consumer<BinlogEvent> handler;
  private long lastSeenId = -1;

  @Override
  public void validate(BinlogEvent event) {
    long eventId = event.getOffset();
    log.debug("Validating order for event with id {}. {}", eventId, event);

    if (lastSeenId > eventId) {
      log.warn(
          "Mutation with id {} is out of order and should precede {}. {}",
          eventId,
          lastSeenId,
          event);
      handler.accept(event);
    }

    lastSeenId = eventId;
  }

  @Override
  public void reset() {
    lastSeenId = -1;
  }
}
