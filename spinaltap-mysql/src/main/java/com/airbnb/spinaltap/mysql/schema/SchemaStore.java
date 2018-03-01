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

public interface SchemaStore<T> {
  /**
   * Create and save schema into schema store, schema version will be generated automatically
   *
   * @param database
   * @param table
   * @param binlogFilePos
   * @param timestamp
   * @param sql
   * @param columnInfoList
   */
  void put(
      String database,
      String table,
      BinlogFilePos binlogFilePos,
      long timestamp,
      String sql,
      List<ColumnInfo> columnInfoList);

  /**
   * Save schema into schema store
   *
   * @param schema
   * @throws Exception
   */
  void put(T schema) throws Exception;

  /**
   * Query table schema by binlogFilePos
   *
   * @param database
   * @param table
   * @param binlogFilePos
   * @throws Exception
   */
  T query(String database, String table, BinlogFilePos binlogFilePos);

  /**
   * Get table schema by schema version
   *
   * @param database
   * @param table
   * @param version
   * @throws Exception
   */
  T get(String database, String table, int version);

  /**
   * Get table schema by BinlogFilePos, return null if not found
   *
   * @param binlogFilePos
   * @return
   */
  T get(BinlogFilePos binlogFilePos);

  /**
   * Get latest schema for a table
   *
   * @param database
   * @param table
   * @throws Exception
   */
  T getLatest(String database, String table);

  /**
   * Get all latest table schemas for a database
   *
   * @param database
   * @return
   * @throws Exception
   */
  Map<String, T> getLatest(String database);

  /**
   * Get latest schema version for a table
   *
   * @param database
   * @param table
   * @return
   * @throws Exception
   */
  int getLatestVersion(String database, String table);

  /**
   * Get all table schemas
   *
   * @return Table<database, table, TreeMap<version, schema>>
   * @throws Exception
   */
  Table<String, String, TreeMap<Integer, T>> getAll();

  /**
   * Get all schemas for a table
   *
   * @param database
   * @param table
   * @return
   * @throws Exception
   */
  TreeMap<Integer, T> getAll(String database, String table);

  /**
   * Check if a table exists in schema store
   *
   * @param database
   * @param table
   * @return
   */
  boolean exists(String database, String table);
}
