/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.mutation;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.Transaction;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MysqlMutationMetadata extends Mutation.Metadata {
  private final DataSource dataSource;
  private final BinlogFilePos filePos;
  private final Table table;
  private final long serverId;
  private final Transaction beginTransaction;
  private final Transaction lastTransaction;

  /** The leader epoch of the node resource processing the event. */
  private final long leaderEpoch;

  /** The mutation row position in the given binlog event. */
  private final int eventRowPosition;

  public MysqlMutationMetadata(
      DataSource dataSource,
      BinlogFilePos filePos,
      Table table,
      long serverId,
      long id,
      long timestamp,
      Transaction beginTransaction,
      Transaction lastTransaction,
      long leaderEpoch,
      int eventRowPosition) {
    super(id, timestamp);

    this.dataSource = dataSource;
    this.filePos = filePos;
    this.table = table;
    this.serverId = serverId;
    this.beginTransaction = beginTransaction;
    this.lastTransaction = lastTransaction;
    this.leaderEpoch = leaderEpoch;
    this.eventRowPosition = eventRowPosition;
  }
}
