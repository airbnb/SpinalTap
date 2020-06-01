/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class MysqlColumnTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final MysqlColumn COLUMN =
      new MysqlColumn("column1", "varchar", "varchar(255)", false);
  private static final List<MysqlColumn> COLUMNS =
      Arrays.asList(
          new MysqlColumn("id", "int", "int(20)", true),
          COLUMN,
          new MysqlColumn("column2", "text", "text", false));

  @Test
  public void testJSONSerDer() throws Exception {
    String jsonString = OBJECT_MAPPER.writeValueAsString(COLUMN);
    MysqlColumn deserialized = OBJECT_MAPPER.readValue(jsonString, MysqlColumn.class);
    assertEquals(COLUMN, deserialized);

    jsonString = OBJECT_MAPPER.writeValueAsString(COLUMNS);
    List<MysqlColumn> deserializedColumns =
        OBJECT_MAPPER.readValue(jsonString, new TypeReference<List<MysqlColumn>>() {});
    assertEquals(COLUMNS, deserializedColumns);
  }
}
