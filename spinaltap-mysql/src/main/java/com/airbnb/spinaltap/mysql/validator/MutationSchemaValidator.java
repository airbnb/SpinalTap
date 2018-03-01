/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.validator;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.Validator;
import com.airbnb.spinaltap.mysql.mutation.MysqlMutation;
import com.airbnb.spinaltap.mysql.mutation.schema.ColumnMetadata;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MutationSchemaValidator implements Validator<MysqlMutation> {
  private final Consumer<Mutation<?>> handler;

  @Override
  public void validate(MysqlMutation mutation) {
    log.debug("Validating schema for mutation: {}", mutation);

    if (!hasValidSchema(mutation.getRow())) {
      log.warn("Invalid schema detected for mutation: {}", mutation);
      handler.accept(mutation);
    }
  }

  public boolean hasValidSchema(Row row) {
    Map<String, ColumnMetadata> tableSchema = row.getTable().getColumns();
    Map<String, ColumnMetadata> rowSchema =
        row.getColumns()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getMetadata()));

    return tableSchema.equals(rowSchema);
  }

  @Override
  public void reset() {}
}
