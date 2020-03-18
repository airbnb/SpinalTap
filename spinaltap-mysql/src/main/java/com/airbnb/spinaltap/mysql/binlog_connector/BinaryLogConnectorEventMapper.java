/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.binlog_connector;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;
import com.github.shyiko.mysql.binlog.event.XidEventData;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Represents a mapper that maps a {@link com.github.shyiko.mysql.binlog.event.Event} to a {@link
 * com.airbnb.spinaltap.mysql.event.BinlogEvent}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BinaryLogConnectorEventMapper {
  public static final BinaryLogConnectorEventMapper INSTANCE = new BinaryLogConnectorEventMapper();

  public Optional<BinlogEvent> map(
      @NonNull final Event event, @NonNull final BinlogFilePos position) {
    final EventHeaderV4 header = event.getHeader();
    final EventType eventType = header.getEventType();
    final long serverId = header.getServerId();
    final long timestamp = header.getTimestamp();

    if (EventType.isWrite(eventType)) {
      final WriteRowsEventData data = event.getData();
      return Optional.of(
          new WriteEvent(data.getTableId(), serverId, timestamp, position, data.getRows()));
    } else if (EventType.isUpdate(eventType)) {
      final UpdateRowsEventData data = event.getData();
      return Optional.of(
          new UpdateEvent(data.getTableId(), serverId, timestamp, position, data.getRows()));
    } else if (EventType.isDelete(eventType)) {
      final DeleteRowsEventData data = event.getData();
      return Optional.of(
          new DeleteEvent(data.getTableId(), serverId, timestamp, position, data.getRows()));
    } else {
      switch (eventType) {
        case TABLE_MAP:
          TableMapEventData tableMapData = event.getData();
          return Optional.of(
              new TableMapEvent(
                  tableMapData.getTableId(),
                  serverId,
                  timestamp,
                  position,
                  tableMapData.getDatabase(),
                  tableMapData.getTable(),
                  tableMapData.getColumnTypes()));
        case XID:
          final XidEventData xidData = event.getData();
          return Optional.of(new XidEvent(serverId, timestamp, position, xidData.getXid()));
        case QUERY:
          final QueryEventData queryData = event.getData();
          return Optional.of(
              new QueryEvent(
                  serverId,
                  timestamp,
                  position,
                  queryData.getDatabase(),
                  queryData
                      .getSql()
                      // https://dev.mysql.com/doc/refman/5.7/en/comments.html
                      // Replace MySQL-specific comments (/*! ... */ and /*!50110 ... */) which
                      // are actually executed
                      .replaceAll("/\\*!(?:\\d{5})?(.*?)\\*/", "$1")
                      // Remove block comments
                      // https://stackoverflow.com/questions/13014947/regex-to-match-a-c-style-multiline-comment
                      // line comments and newlines are kept
                      // Note: This does not handle comments in quotes
                      .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                      // Remove extra spaces
                      .replaceAll("\\h+", " ")
                      .replaceAll("^\\s+", "")));
        case FORMAT_DESCRIPTION:
          return Optional.of(new StartEvent(serverId, timestamp, position));
        default:
          return Optional.empty();
      }
    }
  }
}
