/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;

public class MysqlSchemaTrackerTest {
  private static final String SOURCE_NAME = "source";
  private static final String DATABASE_NAME = "database";
  private static final String DATABASE2_NAME = "database2";
  private static final MysqlTableSchema TABLE1_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table1",
          "",
          Arrays.asList(
              new ColumnInfo("table1", "col1", "TINY", true),
              new ColumnInfo("table1", "col2", "STRING", false),
              new ColumnInfo("table1", "col3", "FLOAT", true),
              new ColumnInfo("table1", "col4", "LONG", false)));

  private static final MysqlTableSchema TABLE1_SCHEMA_UPDATED =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table1",
          "",
          Arrays.asList(
              new ColumnInfo("table1", "col1", "TINY", true),
              new ColumnInfo("table1", "col2", "STRING", false),
              new ColumnInfo("table1", "col3", "FLOAT", true)));

  private static final MysqlTableSchema TABLE1_TMP_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table1_tmp",
          "",
          Arrays.asList(
              new ColumnInfo("table1_tmp", "col1", "TINY", true),
              new ColumnInfo("table1_tmp", "col2", "STRING", false),
              new ColumnInfo("table1_tmp", "col3", "FLOAT", true),
              new ColumnInfo("table1_tmp", "col4", "LONG", false),
              new ColumnInfo("table1_tmp", "col5", "TEXT", false)));

  private static final MysqlTableSchema TABLE1_SCHEMA_RENAMED =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table1",
          "",
          Arrays.asList(
              new ColumnInfo("table1", "col1", "TINY", true),
              new ColumnInfo("table1", "col2", "STRING", false),
              new ColumnInfo("table1", "col3", "FLOAT", true),
              new ColumnInfo("table1", "col4", "LONG", false),
              new ColumnInfo("table1", "col5", "TEXT", false)));

  private static final MysqlTableSchema TABLE1_TMP_SCHEMA_RENAMED =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table1_tmp",
          "",
          Arrays.asList(
              new ColumnInfo("table1_tmp", "col1", "TINY", true),
              new ColumnInfo("table1_tmp", "col2", "STRING", false),
              new ColumnInfo("table1_tmp", "col3", "FLOAT", true),
              new ColumnInfo("table1_tmp", "col4", "LONG", false)));

  private static final MysqlTableSchema TABLE2_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table2",
          "",
          Arrays.asList(
              new ColumnInfo("table2", "col1", "TINY", true),
              new ColumnInfo("table2", "col2", "STRING", false),
              new ColumnInfo("table2", "col3", "FLOAT", true),
              new ColumnInfo("table2", "col4", "LONG", false),
              new ColumnInfo("table2", "col5", "VARCHAR", false)));

  private static final MysqlTableSchema TABLE3_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE_NAME,
          "table3",
          "",
          Arrays.asList(
              new ColumnInfo("table3", "col1", "TINY", true),
              new ColumnInfo("table3", "col2", "STRING", false)));

  private static final MysqlTableSchema TABLE3_DELETED_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME, DATABASE_NAME, "table3", "", Lists.newArrayList());

  private static final MysqlTableSchema DATABASE2_TABLE1_SCHEMA =
      MysqlSchemaUtil.createTableSchema(
          SOURCE_NAME,
          DATABASE2_NAME,
          "table1",
          "",
          Arrays.asList(
              new ColumnInfo("table1", "col1", "TINY", true),
              new ColumnInfo("table1", "col2", "STRING", false),
              new ColumnInfo("table1", "col3", "FLOAT", true)));

  private final SchemaStore<MysqlTableSchema> schemaStore = mock(CachedMysqlSchemaStore.class);
  private final MysqlSchemaDatabase schemaDatabase = mock(MysqlSchemaDatabase.class);
  private final MysqlDDLHistoryStore ddlHistoryStore = mock(MysqlDDLHistoryStore.class);
  private final BinlogFilePos binlogFilePos = new BinlogFilePos("mysql-bin-changelog.000332");
  private final QueryEvent queryEvent =
      new QueryEvent(
          0,
          0,
          binlogFilePos,
          DATABASE_NAME,
          "ALTER TABLE `database`.`table` ADD COLUMN `new_column` VARBINARY ( 255 ) NULL AFTER `last_column`");

  @Before
  public void setUp() throws Exception {
    when(schemaStore.get(binlogFilePos)).thenReturn(null);
    when(ddlHistoryStore.get(binlogFilePos)).thenReturn(null);
  }

  @Test
  public void testDeleteTable() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE2_SCHEMA);
          }
        });

    Map<String, MysqlTableSchema> tableSchemaMapInSchemaDatabase =
        ImmutableMap.of("table1", TABLE1_SCHEMA);

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME)).thenReturn(tableSchemaMapInSchemaDatabase);

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table2",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            Lists.newArrayList());
  }

  @Test
  public void testAddTable() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table3",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(3, TABLE3_DELETED_SCHEMA);
          }
        });

    Map<String, MysqlTableSchema> tableSchemaMapInSchemaDatabase =
        ImmutableMap.of("table1", TABLE1_SCHEMA, "table2", TABLE2_SCHEMA, "table3", TABLE3_SCHEMA);

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME)).thenReturn(tableSchemaMapInSchemaDatabase);

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table2",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE2_SCHEMA.getColumnInfo());
    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table3",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE3_SCHEMA.getColumnInfo());
  }

  @Test
  public void testUpdateTable() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE2_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table3",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(3, TABLE3_DELETED_SCHEMA);
          }
        });

    Map<String, MysqlTableSchema> tableSchemaMapInSchemaDatabase =
        ImmutableMap.of("table1", TABLE1_SCHEMA_UPDATED, "table2", TABLE2_SCHEMA);

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME)).thenReturn(tableSchemaMapInSchemaDatabase);

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table1",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE1_SCHEMA_UPDATED.getColumnInfo());
  }

  @Test
  public void testAddUpdateDeleteTables() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE2_SCHEMA);
          }
        });

    Map<String, MysqlTableSchema> tableSchemaMapInSchemaDatabase =
        ImmutableMap.of("table1", TABLE1_SCHEMA_UPDATED, "table3", TABLE3_SCHEMA);

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME)).thenReturn(tableSchemaMapInSchemaDatabase);

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table2",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            Lists.newArrayList());
    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table1",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE1_SCHEMA_UPDATED.getColumnInfo());
    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table3",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE3_SCHEMA.getColumnInfo());
  }

  @Test
  public void testRenameTable() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE2_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1_tmp",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_TMP_SCHEMA);
          }
        });

    Map<String, MysqlTableSchema> tableSchemaMapInSchemaDatabase =
        ImmutableMap.of(
            "table1",
            TABLE1_SCHEMA_RENAMED,
            "table2",
            TABLE2_SCHEMA,
            "table1_tmp",
            TABLE1_TMP_SCHEMA_RENAMED);

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME)).thenReturn(tableSchemaMapInSchemaDatabase);

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table1_tmp",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE1_TMP_SCHEMA_RENAMED.getColumnInfo());
    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table1",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            TABLE1_SCHEMA_RENAMED.getColumnInfo());
  }

  @Test
  public void testCreateDatabase() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE2_SCHEMA);
          }
        });

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE_NAME, DATABASE2_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME))
        .thenReturn(ImmutableMap.of("table1", TABLE1_SCHEMA, "table2", TABLE2_SCHEMA));
    when(schemaDatabase.fetchTableSchema(DATABASE2_NAME))
        .thenReturn(ImmutableMap.of("table1", DATABASE2_TABLE1_SCHEMA));

    QueryEvent queryEvent =
        new QueryEvent(0, 0, binlogFilePos, DATABASE2_NAME, "CREATE DATABASE `database2`");

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaDatabase).applyDDLStatement("", queryEvent.getSql());
    verify(schemaStore)
        .put(
            DATABASE2_NAME,
            "table1",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            DATABASE2_TABLE1_SCHEMA.getColumnInfo());
  }

  @Test
  public void testDropDatabase() throws Exception {
    Table<String, String, TreeMap<Integer, MysqlTableSchema>> allTableSchemaInStore =
        Tables.newCustomTable(Maps.newHashMap(), Maps::newHashMap);
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE1_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE_NAME,
        "table2",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, TABLE2_SCHEMA);
          }
        });
    allTableSchemaInStore.put(
        DATABASE2_NAME,
        "table1",
        new TreeMap<Integer, MysqlTableSchema>() {
          {
            put(1, DATABASE2_TABLE1_SCHEMA);
          }
        });

    when(schemaDatabase.listDatabases()).thenReturn(Sets.newHashSet(DATABASE2_NAME));
    when(schemaStore.getAll()).thenReturn(allTableSchemaInStore);
    when(schemaDatabase.fetchTableSchema(DATABASE_NAME)).thenReturn(ImmutableMap.of());
    when(schemaDatabase.fetchTableSchema(DATABASE2_NAME))
        .thenReturn(ImmutableMap.of("table1", DATABASE2_TABLE1_SCHEMA));

    QueryEvent queryEvent =
        new QueryEvent(0, 0, binlogFilePos, DATABASE2_NAME, "DROP DATABASE `database1`");

    SchemaTracker schemaTracker =
        new MysqlSchemaTracker(schemaStore, schemaDatabase, ddlHistoryStore);

    schemaTracker.processDDLStatement(queryEvent);

    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table1",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            Lists.newArrayList());
    verify(schemaStore)
        .put(
            DATABASE_NAME,
            "table2",
            queryEvent.getBinlogFilePos(),
            queryEvent.getTimestamp(),
            queryEvent.getSql(),
            Lists.newArrayList());
  }
}
