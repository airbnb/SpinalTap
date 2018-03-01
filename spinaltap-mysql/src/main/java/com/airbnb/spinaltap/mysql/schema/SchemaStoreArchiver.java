/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

public interface SchemaStoreArchiver {
  /** Archive all databases in a schema store */
  void archiveAll();

  /**
   * Archive schema store for database
   *
   * @param database
   */
  void archive(String database);
}
