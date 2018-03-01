/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import java.util.Set;

public class MysqlInsertMutation extends MysqlMutation {
  public MysqlInsertMutation(MysqlMutationMetadata metadata, Row row) {
    super(metadata, Type.INSERT, row);
  }

  @Override
  public Set<String> getChangedColumns() {
    return getRow().getColumns().keySet();
  }
}
