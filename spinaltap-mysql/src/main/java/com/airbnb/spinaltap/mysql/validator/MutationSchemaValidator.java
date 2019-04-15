/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.validator;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.Validator;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link Validator} that asserts parity of the {@link
 * com.airbnb.spinaltap.mysql.mutation.schema.Table} schema with the {@link
 * com.airbnb.spinaltap.mysql.mutation.schema.Column} schema associated with a {@link MysqlMutation}
 */
@Slf4j
@RequiredArgsConstructor
public final class MutationSchemaValidator implements Validator<MysqlMutation> {
  /** The handler to call on {@link Mutation}s that are invalid. */
  @NonNull private final Consumer<Mutation<?>> handler;

  @Override
  public void validate(@NonNull final MysqlMutation mutation) {
    log.debug("Validating schema for mutation: {}", mutation);

    if (!hasValidSchema(mutation.getRow())) {
      log.warn("Invalid schema detected for mutation: {}", mutation);
      handler.accept(mutation);
    }
  }

  private boolean hasValidSchema(final Row row) {
    return row.getColumns()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getMetadata()))
        .equals(row.getTable().getColumns());
  }

  @Override
  public void reset() {}
}
