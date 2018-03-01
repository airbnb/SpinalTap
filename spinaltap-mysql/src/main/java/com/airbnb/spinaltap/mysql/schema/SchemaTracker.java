/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.event.QueryEvent;

public interface SchemaTracker {
  /**
   * Apply DDL statement in QueryEvent, update schema version if needed.
   *
   * @param event
   */
  void processDDLStatement(QueryEvent event);
}
