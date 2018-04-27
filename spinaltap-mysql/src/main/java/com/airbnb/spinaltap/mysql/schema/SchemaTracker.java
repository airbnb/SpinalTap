/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.event.QueryEvent;

/**
 * Responsible for keeping track of schema changes on databases of a {@link
 * com.airbnb.spinaltap.common.source.Source}, by processing DDL statement from {@link QueryEvent}s
 * and updating the schema version if needed.
 */
@FunctionalInterface
public interface SchemaTracker {
  void processDDLStatement(QueryEvent event);
}
