/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MysqlSchemaUtilTest {
  @Test
  public void testBlockSQLCommentsRemoval() {
    String sql_with_block_comments =
        "CREATE/* ! COMMENTS ! */UNIQUE /* ANOTHER COMMENTS ! */INDEX unique_index\n"
            + "ON `my_db`.`my_table` (`col1`, `col2`)";
    String sql_with_comments_in_multi_lines =
        "CREATE UNIQUE /*\n"
            + "COMMENT Line1  \n"
            + "COMMENT Line 2\n"
            + "*/\n"
            + "INDEX ON `my_db`.`my_table` (`col1`, `col2`)";
    String expected_sql =
        "CREATE UNIQUE INDEX unique_index\nON `my_db`.`my_table` (`col1`, `col2`)";
    String stripped_sql = MysqlSchemaUtil.removeCommentsFromDDL(sql_with_block_comments);
    assertEquals(expected_sql, stripped_sql);

    stripped_sql = MysqlSchemaUtil.removeCommentsFromDDL(sql_with_comments_in_multi_lines);
    expected_sql = "CREATE UNIQUE \nINDEX ON `my_db`.`my_table` (`col1`, `col2`)";
    assertEquals(expected_sql, stripped_sql);
  }

  @Test
  public void testMySQLSpecCommentsRemoval() {
    String sql_with_mysql_spec_comments =
        "CREATE TABLE t1(a INT, KEY (a)) /*!50110 KEY_BLOCK_SIZE=1024 */";
    String sql_with_mysql_spec_comments2 = "/*!CREATE TABLE t1(a INT, KEY (a))*/";

    String expected_sql = "CREATE TABLE t1(a INT, KEY (a)) KEY_BLOCK_SIZE=1024 ";
    String stripped_sql = MysqlSchemaUtil.removeCommentsFromDDL(sql_with_mysql_spec_comments);
    assertEquals(expected_sql, stripped_sql);

    expected_sql = "CREATE TABLE t1(a INT, KEY (a))";
    stripped_sql = MysqlSchemaUtil.removeCommentsFromDDL(sql_with_mysql_spec_comments2);
    assertEquals(expected_sql, stripped_sql);
  }
}
