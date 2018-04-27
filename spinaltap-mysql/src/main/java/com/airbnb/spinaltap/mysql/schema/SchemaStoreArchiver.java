/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

/**
 * Responsible for archiving the {@link SchemaStore} for a {@link
 * com.airbnb.spinaltap.common.source.Source} database.
 */
public interface SchemaStoreArchiver {
  void archiveAll();

  void archive(String database);
}
