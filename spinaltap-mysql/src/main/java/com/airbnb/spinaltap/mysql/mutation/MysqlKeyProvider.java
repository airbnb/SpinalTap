/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.KeyProvider;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Represents a {@link KeyProvider} for {@link MysqlMutation}s. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MysqlKeyProvider implements KeyProvider<Mutation<?>, String> {
  public static final MysqlKeyProvider INSTANCE = new MysqlKeyProvider();

  /**
   * @return the key for a {@link MysqlMutation} in the following format:
   *     "[database_name][table_name][primary_key_value]".
   */
  @Override
  public String get(@NonNull final Mutation<?> mutation) {
    Preconditions.checkState(mutation instanceof MysqlMutation);

    final MysqlMutation mysqlMutation = (MysqlMutation) mutation;
    final Table table = mysqlMutation.getMetadata().getTable();
    final Row row = mysqlMutation.getRow();

    return String.format(
        "%s:%s:%s", table.getDatabase(), table.getName(), row.getPrimaryKeyValue());
  }
}
