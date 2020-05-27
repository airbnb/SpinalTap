/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import org.jdbi.v3.core.Jdbi;
import org.junit.Test;

public class MysqlSchemaDatabaseTest {
  private static final String SOURCE_NAME = "source";
  private final Jdbi jdbi = mock(Jdbi.class);
  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);
  private final MysqlSchemaDatabase schemaDatabase =
      new MysqlSchemaDatabase(SOURCE_NAME, jdbi, metrics);;

  @Test
  public void testAddSourcePrefixCreateTable() throws Exception {
    String ddl =
        "create table `gibraltar_production`.`_instrument_details_paypal_new` (\n"
            + "  `instrument_token` varbinary(255) NOT NULL,\n"
            + "  `version` int(11) NOT NULL,\n"
            + "  `paypal_email_encrypted` varbinary(255) NOT NULL,\n"
            + "  `created_at` datetime NOT NULL,\n"
            + "  PRIMARY KEY (`instrument_token`,`version`),\n"
            + "  KEY `index_instrument_details_paypal_paypal_email` (`paypal_email_encrypted`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8";
    String expectedDDL =
        "create table `source/gibraltar_production`.`_instrument_details_paypal_new` (\n"
            + "  `instrument_token` varbinary(255) NOT NULL,\n"
            + "  `version` int(11) NOT NULL,\n"
            + "  `paypal_email_encrypted` varbinary(255) NOT NULL,\n"
            + "  `created_at` datetime NOT NULL,\n"
            + "  PRIMARY KEY (`instrument_token`,`version`),\n"
            + "  KEY `index_instrument_details_paypal_paypal_email` (`paypal_email_encrypted`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl =
        "CREATE TABLE table123 (\n"
            + "`id` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "`name` varchar(255) NOT NULL\n"
            + ") ENGINE=InnoDB AUTO_INCREMENT=2145755390 DEFAULT CHARSET=latin1";
    assertEquals(ddl, schemaDatabase.addSourcePrefix(ddl));

    ddl =
        "CREATE TABLE `table1234` (\n"
            + "`id` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "`name` varchar(255) NOT NULL\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=latin1";
    assertEquals(ddl, schemaDatabase.addSourcePrefix(ddl));

    ddl =
        "CREATE TABLE IF NOT EXISTS my_database.`my_table` (\n"
            + "`id` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "`name` varchar(255) NOT NULL\n"
            + ") ENGINE=InnoDB AUTO_INCREMENT=2145755390 DEFAULT CHARSET=latin1";
    expectedDDL =
        "CREATE TABLE IF NOT EXISTS `source/my_database`.`my_table` (\n"
            + "`id` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "`name` varchar(255) NOT NULL\n"
            + ") ENGINE=InnoDB AUTO_INCREMENT=2145755390 DEFAULT CHARSET=latin1";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl =
        "create table `test3`.`test_table` (\n"
            + "   `id` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "   `name` varchar(45) DEFAULT NULL,\n"
            + "   `balance` varchar(45) DEFAULT NULL,   \n"
            + "   `timestamp` timestamp NULL DEFAULT NULL,\n"
            + "   `float_col` float DEFAULT NULL,\n"
            + "   `created_at` datetime DEFAULT NULL,\n"
            + "   `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE NOW(),\n"
            + "   PRIMARY KEY (`id`) ) ENGINE=InnoDB";
    expectedDDL =
        "create table `source/test3`.`test_table` (\n"
            + "   `id` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "   `name` varchar(45) DEFAULT NULL,\n"
            + "   `balance` varchar(45) DEFAULT NULL,   \n"
            + "   `timestamp` timestamp NULL DEFAULT NULL,\n"
            + "   `float_col` float DEFAULT NULL,\n"
            + "   `created_at` datetime DEFAULT NULL,\n"
            + "   `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE NOW(),\n"
            + "   PRIMARY KEY (`id`) ) ENGINE=InnoDB";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl =
        "create   table `airbed3_production`.`_users_ghc` ( \t\t\t"
            + "id bigint auto_increment, \t\t\t"
            + "last_update timestamp not null DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, \t\t\t"
            + "hint varchar(64) charset   ascii not null, \t\t\t"
            + "value varchar(4096) charset ascii not null, \t\t\t"
            + "primary key(id), \t\t\t"
            + "unique key hint_uidx(hint) \t\t\t"
            + ") auto_increment=256";
    expectedDDL =
        "create   table `source/airbed3_production`.`_users_ghc` ( \t\t\t"
            + "id bigint auto_increment, \t\t\t"
            + "last_update timestamp not null DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, \t\t\t"
            + "hint varchar(64) charset   ascii not null, \t\t\t"
            + "value varchar(4096) charset ascii not null, \t\t\t"
            + "primary key(id), \t\t\t"
            + "unique key hint_uidx(hint) \t\t\t"
            + ") auto_increment=256";
    String d = schemaDatabase.addSourcePrefix(ddl);
    assertEquals(expectedDDL, d);
  }

  @Test
  public void testAddSourcePrefixAlterTable() throws Exception {
    String ddl =
        "ALTER TABLE `gibraltar_production`.`_instrument_details_paypal_new`\n"
            + "ADD COLUMN `account_id` VARBINARY ( 255 ) NULL AFTER `paypal_email_encrypted`,\n"
            + "ADD COLUMN `first_name` VARBINARY ( 255 ) NULL AFTER `account_id`,\n"
            + "ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP";
    String expectedDDL =
        "ALTER TABLE `source/gibraltar_production`.`_instrument_details_paypal_new`\n"
            + "ADD COLUMN `account_id` VARBINARY ( 255 ) NULL AFTER `paypal_email_encrypted`,\n"
            + "ADD COLUMN `first_name` VARBINARY ( 255 ) NULL AFTER `account_id`,\n"
            + "ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl = "ALTER TABLE my_test_table RENAME TO `tmp`.`my_test_table_1234`;";
    expectedDDL = "ALTER TABLE my_test_table RENAME TO `source/tmp`.`my_test_table_1234`;";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl = "ALTER TABLE test_db.test_table DROP INDEX some_index";
    expectedDDL = "ALTER TABLE `source/test_db`.`test_table` DROP INDEX some_index";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl = "ALTER TABLE `test_table123` DROP COLUMN `some_column`";
    assertEquals(ddl, schemaDatabase.addSourcePrefix(ddl));
  }

  @Test
  public void testAddSourcePrefixRenameTable() throws Exception {
    String ddl = "RENAME TABLE `db1`.`table1` TO `tmp`.`table1`";
    String expectedDDL = "RENAME TABLE `source/db1`.`table1` TO `source/tmp`.`table1`";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl =
        "RENAME TABLE airbed3_production.20170810023312170_reservation2s to tmp.20170810023312170_reservation2s";
    expectedDDL =
        "RENAME TABLE `source/airbed3_production`.`20170810023312170_reservation2s` to `source/tmp`.`20170810023312170_reservation2s`";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl = "RENAME TABLE `table123` TO new_table_123, some_db.table123 TO `other_db`.table456";
    expectedDDL =
        "RENAME TABLE `table123` TO new_table_123, `source/some_db`.`table123` TO `source/other_db`.`table456`";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));
  }

  @Test
  public void testAddSourcePrefixDropTable() throws Exception {
    String ddl =
        "DROP TABLE my_table_111, `another_table_222`, my_db.my_table_333, my_db222.`my_table000`, `my_db123`.my_table_444, `my_db_333`.`my_table_555`";
    String expectedDDL =
        "DROP TABLE my_table_111, `another_table_222`, `source/my_db`.`my_table_333`, `source/my_db222`.`my_table000`, `source/my_db123`.`my_table_444`, `source/my_db_333`.`my_table_555`";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl = "DROP TABLE IF EXISTS `db123`.`table456`";
    expectedDDL = "DROP TABLE IF EXISTS `source/db123`.`table456`";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));

    ddl = "DROP TABLE ```escaped_backquotes``test_db:@fds!aaa`.`DFDS..``table``sss`";
    expectedDDL = "DROP TABLE `source/``escaped_backquotes``test_db:@fds!aaa`.`DFDS..``table``sss`";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));
  }

  @Test
  public void testAddSourcePrefixCreateDatabase() throws Exception {
    String ddl = "CREATE DATABASE new_database";
    assertEquals("CREATE DATABASE `source/new_database`", schemaDatabase.addSourcePrefix(ddl));

    ddl = "CREATE DATABASE `another_new_database`";
    assertEquals(
        "CREATE DATABASE `source/another_new_database`", schemaDatabase.addSourcePrefix(ddl));

    ddl = "CREATE SCHEMA `new_database_123`";
    assertEquals("CREATE SCHEMA `source/new_database_123`", schemaDatabase.addSourcePrefix(ddl));

    ddl = "CREATE SCHEMA `new_``schema``+456.789`";
    assertEquals(
        "CREATE SCHEMA `source/new_``schema``+456.789`", schemaDatabase.addSourcePrefix(ddl));

    ddl = "CREATE DATABASE new_db DEFAULT CHARSET=utf8";
    assertEquals(
        "CREATE DATABASE `source/new_db` DEFAULT CHARSET=utf8",
        schemaDatabase.addSourcePrefix(ddl));
  }

  @Test
  public void testAddSourcePrefixDropDatabase() throws Exception {
    String ddl = "DROP DATABASE old_database";
    assertEquals("DROP DATABASE `source/old_database`", schemaDatabase.addSourcePrefix(ddl));

    ddl = "DROP SCHEMA `old_database`";
    assertEquals("DROP SCHEMA `source/old_database`", schemaDatabase.addSourcePrefix(ddl));
  }

  @Test
  public void testAddSourcePrefixCreateIndex() throws Exception {
    String ddl = "CREATE INDEX id_index ON lookup (id) USING BTREE;";
    assertEquals(ddl, schemaDatabase.addSourcePrefix(ddl));

    ddl = "CREATE UNIQUE INDEX unique_index ON `my_db`.`my_table` (`col1`, `col2`)";
    String expectedDDL =
        "CREATE UNIQUE INDEX unique_index ON `source/my_db`.`my_table` (`col1`, `col2`)";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));
  }

  @Test
  public void testAddSourcePrefixDropIndex() throws Exception {
    String ddl = "DROP INDEX `index123` ON table";
    assertEquals(ddl, schemaDatabase.addSourcePrefix(ddl));

    ddl = "DROP INDEX `index222` ON `db_name`.`table22`;";
    String expectedDDL = "DROP INDEX `index222` ON `source/db_name`.`table22`;";
    assertEquals(expectedDDL, schemaDatabase.addSourcePrefix(ddl));
  }
}
