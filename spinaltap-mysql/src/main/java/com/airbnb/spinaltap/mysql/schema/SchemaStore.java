/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.google.common.collect.Table;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Represents a store for table schemas. */
public interface SchemaStore<T> {
  /**
   * Create and save schema into {@link SchemaStore}, schema version will be generated automatically
   */
  void put(
      String database,
      String table,
      BinlogFilePos binlogFilePos,
      long timestamp,
      String sql,
      List<ColumnInfo> columnInfoList);

  /** Save schema into {@link SchemaStore} */
  void put(T schema) throws Exception;

  /** Query table schema by {@link BinlogFilePos} */
  T query(String database, String table, BinlogFilePos binlogFilePos);

  /** Get table schema by schema version */
  T get(String database, String table, int version);

  /** Get table schema by BinlogFilePos, return null if not found */
  T get(BinlogFilePos binlogFilePos);

  /** Get latest schema for a table */
  T getLatest(String database, String table);

  /** Get all latest table schemas for a database */
  Map<String, T> getLatest(String database);

  /** Get latest schema version for a table */
  int getLatestVersion(String database, String table);

  /** Get all table schemas */
  Table<String, String, TreeMap<Integer, T>> getAll();

  /** Get all schemas for a table */
  TreeMap<Integer, T> getAll(String database, String table);

  /** Check if a table exists in schema store */
  boolean exists(String database, String table);
}
