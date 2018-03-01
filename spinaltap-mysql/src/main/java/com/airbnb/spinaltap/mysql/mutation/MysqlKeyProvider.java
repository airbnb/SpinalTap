/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.KeyProvider;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.base.Preconditions;

public class MysqlKeyProvider implements KeyProvider<Mutation<?>, String> {
  public static final MysqlKeyProvider INSTANCE = new MysqlKeyProvider();

  /**
   * This is currently a replication of the logic to get the partition for a Mysql table mutation in
   * {@link com.airbnb.jitney.helpers.SpinaltapHelper}
   */
  @Override
  public String get(Mutation<?> mutation) {
    Preconditions.checkState(mutation instanceof MysqlMutation);
    MysqlMutation mysqlMutation = (MysqlMutation) mutation;

    Table table = mysqlMutation.getMetadata().getTable();
    Row row = mysqlMutation.getRow();

    return String.format(
        "%s:%s:%s", table.getDatabase(), table.getName(), row.getPrimaryKeyValue());
  }
}
