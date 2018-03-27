/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.validator;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.Validator;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Responsible for validating that {@link Mutation}s are streamed in order. This is typically used
 * as a {@link com.airbnb.spinaltap.common.source.Source.Listener} or
 * {@link com.airbnb.spinaltap.common.destination.Destination.Listener} to enforce {@link Mutation}
 * ordering guarantee in run-time.
 */
@Slf4j
@RequiredArgsConstructor
public final class MutationOrderValidator implements Validator<Mutation<?>> {
  /**
   * The handler to trigger on an out-of-order {@link Mutation}.
   */
  @NonNull private final Consumer<Mutation<?>> handler;

  /**
   * The id of the last {@link Mutation} validated so far.
   */
  private AtomicLong lastSeenId = new AtomicLong(-1);

  /**
   * Validates a {@link Mutation} is received in order, otherwise triggers the specified handler.
   */
  @Override
  public void validate(@NonNull final Mutation<?> mutation) {
    final long mutationId = mutation.getMetadata().getId();
    log.debug("Validating order for mutation with id {}.", mutationId);

    if (lastSeenId.get() > mutationId) {
      log.warn("Mutation with id {} is out of order and should precede {}.", mutationId, lastSeenId);
      handler.accept(mutation);
    }

    lastSeenId.set(mutationId);
  }

  /**
   * Resets the state of the {@link Validator}.
   */
  @Override
  public void reset() {
    lastSeenId.set(-1);
  }
}
