/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import java.util.Set;
import lombok.ToString;

/** Represents a MySQL {@link Mutation} derived from a binlog event. */
@ToString(callSuper = true)
public abstract class MysqlMutation extends Mutation<Row> {
  public MysqlMutation(MysqlMutationMetadata metadata, Mutation.Type type, Row row) {
    super(metadata, type, row);
  }

  public final Row getRow() {
    return getEntity();
  }

  /** @return columns of the table that have changed value as a result of this mutation */
  public abstract Set<String> getChangedColumns();

  @Override
  public final MysqlMutationMetadata getMetadata() {
    return (MysqlMutationMetadata) super.getMetadata();
  }
}
