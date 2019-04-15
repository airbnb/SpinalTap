/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.validator;

import com.airbnb.spinaltap.common.util.Validator;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link Validator} that asserts {@link BinlogEvent}s are streamed in order of event
 * id (offset). The implement assumes {@code validate} is called on events in the order they are
 * received.
 */
@Slf4j
@RequiredArgsConstructor
public class EventOrderValidator implements Validator<BinlogEvent> {
  /** The handler to call on {@link BinlogEvent}s that are out of order. */
  @NonNull private final Consumer<BinlogEvent> handler;

  private long lastSeenId = -1;

  @Override
  public void validate(@NonNull final BinlogEvent event) {
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
