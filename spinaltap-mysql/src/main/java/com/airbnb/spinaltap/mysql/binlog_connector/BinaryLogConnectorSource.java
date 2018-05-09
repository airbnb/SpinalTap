/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.binlog_connector;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.MysqlSource;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.StateHistory;
import com.airbnb.spinaltap.mysql.StateRepository;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.exception.InvalidBinlogPositionException;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.google.common.base.Preconditions;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.validation.constraints.Min;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link MysqlSource} implement based on open-source library <a
 * href="https://github.com/shyiko/mysql-binlog-connector-java">.
 */
@Slf4j
public final class BinaryLogConnectorSource extends MysqlSource {
  private static final String INVALID_BINLOG_POSITION_ERROR_CODE = "1236";

  @NonNull private final BinaryLogClient client;

  public BinaryLogConnectorSource(
      @NonNull final String name,
      @NonNull final DataSource dataSource,
      @NonNull final BinaryLogClient client,
      @NonNull final Set<String> tableNames,
      @NonNull final TableCache tableCache,
      @NonNull final StateRepository stateRepository,
      @NonNull final StateHistory stateHistory,
      @NonNull final BinlogFilePos initialBinlogFilePosition,
      @NonNull final SchemaTracker schemaTracker,
      @NonNull final MysqlSourceMetrics metrics,
      @NonNull final AtomicLong currentLeaderEpoch,
      @Min(0) final int socketTimeoutInSeconds) {
    super(
        name,
        dataSource,
        tableNames,
        tableCache,
        stateRepository,
        stateHistory,
        initialBinlogFilePosition,
        schemaTracker,
        metrics,
        currentLeaderEpoch,
        new AtomicReference<>(),
        new AtomicReference<>());

    this.client = client;
    initializeClient(socketTimeoutInSeconds);
  }

  /** Initializes the {@link BinaryLogClient}. */
  private void initializeClient(final int socketTimeoutInSeconds) {
    client.setThreadFactory(
        runnable ->
            new Thread(
                runnable,
                String.format(
                    "binlog-client-%s-%s-%d",
                    name, getDataSource().getHost(), getDataSource().getPort())));

    client.setSocketFactory(
        () -> {
          Socket socket = new Socket();
          try {
            if (socketTimeoutInSeconds > 0) {
              socket.setSoTimeout(socketTimeoutInSeconds * 1000);
            }
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
          return socket;
        });

    client.setKeepAlive(false);
    client.registerEventListener(new BinlogEventListener());
    client.registerLifecycleListener(new BinlogClientLifeCycleListener());
  }

  @Override
  protected void connect() throws Exception {
    client.connect();
  }

  @Override
  protected void disconnect() throws Exception {
    client.disconnect();
  }

  @Override
  protected boolean isConnected() {
    return client.isConnected();
  }

  @Override
  public void setPosition(@NonNull final BinlogFilePos pos) {
    log.info("Setting binlog position for source {} to {}", name, pos);

    client.setBinlogFilename(pos.getFileName());
    client.setBinlogPosition(pos.getNextPosition());
  }

  private final class BinlogEventListener implements BinaryLogClient.EventListener {
    public void onEvent(Event event) {
      Preconditions.checkState(isStarted(), "Source is not started and should not process events");

      final EventHeaderV4 header = event.getHeader();
      final BinlogFilePos filePos =
          new BinlogFilePos(
              client.getBinlogFilename(), header.getPosition(), header.getNextPosition());

      BinaryLogConnectorEventMapper.INSTANCE
          .map(event, filePos)
          .ifPresent(BinaryLogConnectorSource.super::processEvent);
    }
  }

  /**
   * Lifecycle listener methods are called synchronized in BinaryLogClient. We should not enter
   * critical sections in SpinalTap code path to avoid deadlocks
   */
  private final class BinlogClientLifeCycleListener implements BinaryLogClient.LifecycleListener {
    public void onConnect(BinaryLogClient client) {
      log.info("Connected to source {}.", name);
      metrics.clientConnected();
    }

    public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
      log.error(
          String.format(
              "Communication failure from source %s, binlogFile=%s, binlogPos=%s",
              name, client.getBinlogFilename(), client.getBinlogPosition()),
          ex);

      if (ex.getMessage().startsWith(INVALID_BINLOG_POSITION_ERROR_CODE)) {
        ex =
            new InvalidBinlogPositionException(
                String.format(
                    "Invalid position %s in binlog file %s",
                    client.getBinlogPosition(), client.getBinlogFilename()));
      }

      onCommunicationError(ex);
    }

    public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
      log.error(
          String.format(
              "Deserialization failure from source %s, BinlogFile=%s, binlogPos=%s",
              name, client.getBinlogFilename(), client.getBinlogPosition()),
          ex);

      onDeserializationError(ex);
    }

    public void onDisconnect(BinaryLogClient client) {
      log.info(
          "Disconnected from source {}. BinlogFile={}, binlogPos={}",
          name,
          client.getBinlogFilename(),
          client.getBinlogPosition());
      metrics.clientDisconnected();
      started.set(false);
    }
  }
}
