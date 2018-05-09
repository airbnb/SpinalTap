/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import java.util.Set;

public final class MysqlDeleteMutation extends MysqlMutation {
  public MysqlDeleteMutation(MysqlMutationMetadata metadata, Row row) {
    super(metadata, Mutation.Type.DELETE, row);
  }

  @Override
  public Set<String> getChangedColumns() {
    return getRow().getColumns().keySet();
  }
}
