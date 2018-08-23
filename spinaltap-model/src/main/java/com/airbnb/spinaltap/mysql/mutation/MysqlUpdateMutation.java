/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.mysql.mutation.schema.Column;
import com.airbnb.spinaltap.mysql.mutation.schema.Row;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public final class MysqlUpdateMutation extends MysqlMutation {
  private final Row previousRow;

  public MysqlUpdateMutation(
      final MysqlMutationMetadata metadata, final Row previousRow, final Row row) {
    super(metadata, Mutation.Type.UPDATE, row);

    this.previousRow = previousRow;
  }

  @Override
  public Set<String> getChangedColumns() {
    // Transform the column values of each Row to Map<String, Serializable>. Map values of type
    // byte[], or columns of type BLOB, will be tested for equality using deepEquals in method
    // getUpdatedColumns.  If we simply passed down the Map<String, Column> of each Row, then
    // deepEquals would in turn call the equals method of type Column, which will wrongly not use
    // deepEquals to compare byte[] values.
    return Mutation.getUpdatedColumns(asColumnValues(getPreviousRow()), asColumnValues(getRow()));
  }

  @VisibleForTesting
  static Map<String, Serializable> asColumnValues(@NonNull final Row row) {
    return Maps.transformValues(row.getColumns(), Column::getValue);
  }
}
