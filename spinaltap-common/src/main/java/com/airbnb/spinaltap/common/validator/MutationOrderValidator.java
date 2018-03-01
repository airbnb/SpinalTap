/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.validator;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.Validator;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MutationOrderValidator implements Validator<Mutation<?>> {
  private final Consumer<Mutation<?>> handler;
  private long lastSeenId = -1;

  @Override
  public void validate(Mutation<?> mutation) {
    long mutationId = mutation.getMetadata().getId();
    log.debug("Validating order for mutation with id {}. {}", mutationId, mutation);

    if (lastSeenId > mutationId) {
      log.warn(
          "Mutation with id {} is out of order and should precede {}. {}",
          mutationId,
          lastSeenId,
          mutation);
      handler.accept(mutation);
    }

    lastSeenId = mutationId;
  }

  @Override
  public void reset() {
    lastSeenId = -1;
  }
}
