/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.event.filter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Filter;
import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.airbnb.spinaltap.mysql.mutation.schema.Table;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class MysqlEventFilterTest {
  private static final String DATABASE_NAME = "db";
  private static final String TABLE_NAME = "users";
  private static final long TABLE_ID = 1l;
  private static final Set<String> TABLE_NAMES =
      Sets.newHashSet(Table.canonicalNameOf(DATABASE_NAME, TABLE_NAME));
  private static final BinlogFilePos BINLOG_FILE_POS = new BinlogFilePos("test.123", 14, 100);

  @Test
  public void testEventFilter() throws Exception {
    TableCache tableCache = mock(TableCache.class);
    BinlogEvent lastEvent = new XidEvent(0l, 0l, BINLOG_FILE_POS, 0l);
    BinlogFilePos nextPosition = new BinlogFilePos("test.123", 15, 100);
    SourceState state = new SourceState(0l, lastEvent.getOffset(), 0l, BINLOG_FILE_POS);
    Filter<BinlogEvent> filter =
        MysqlEventFilter.create(tableCache, TABLE_NAMES, new AtomicReference(state));

    when(tableCache.contains(TABLE_ID)).thenReturn(true);

    assertTrue(
        filter.apply(
            new TableMapEvent(
                TABLE_ID, 0l, 0l, nextPosition, DATABASE_NAME, TABLE_NAME, new byte[1])));
    assertTrue(
        filter.apply(new WriteEvent(TABLE_ID, 0l, 0l, nextPosition, Collections.emptyList())));
    assertTrue(
        filter.apply(new DeleteEvent(TABLE_ID, 0l, 0l, nextPosition, Collections.emptyList())));
    assertTrue(
        filter.apply(new UpdateEvent(TABLE_ID, 0l, 0l, nextPosition, Collections.emptyList())));
    assertTrue(filter.apply(new XidEvent(0l, 0l, BINLOG_FILE_POS, 12l)));
    assertTrue(filter.apply(new QueryEvent(0l, 0l, BINLOG_FILE_POS, DATABASE_NAME, "")));
    assertTrue(filter.apply(new StartEvent(0l, 0l, BINLOG_FILE_POS)));

    assertFalse(
        filter.apply(new TableMapEvent(TABLE_ID, 0l, 0l, BINLOG_FILE_POS, "", "", new byte[1])));
    assertFalse(filter.apply(new WriteEvent(0l, 0l, 0l, BINLOG_FILE_POS, Collections.emptyList())));
    assertFalse(
        filter.apply(new WriteEvent(TABLE_ID, 0l, 0l, BINLOG_FILE_POS, Collections.emptyList())));
    assertFalse(filter.apply(mock(BinlogEvent.class)));
  }
}
