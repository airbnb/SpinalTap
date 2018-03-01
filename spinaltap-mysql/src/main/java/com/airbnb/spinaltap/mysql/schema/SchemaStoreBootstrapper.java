/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

public interface SchemaStoreBootstrapper {
  /**
   * Bootstrap schema store for a database
   *
   * @param database
   */
  void bootstrap(String database);

  /** Bootstrap schema store for all databases */
  void bootstrapAll();
}
