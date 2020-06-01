/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
public class MysqlTableSchema {
  long id;
  String database;
  String table;
  BinlogFilePos binlogFilePos;
  String gtid;
  String sql;
  long timestamp;
  List<MysqlColumn> columns;
  Map<String, String> metadata;
}
