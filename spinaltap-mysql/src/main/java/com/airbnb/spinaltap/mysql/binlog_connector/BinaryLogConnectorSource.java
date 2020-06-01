/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.binlog_connector;

import com.airbnb.spinaltap.mysql.BinlogFilePos;
import com.airbnb.spinaltap.mysql.DataSource;
import com.airbnb.spinaltap.mysql.MysqlClient;
import com.airbnb.spinaltap.mysql.MysqlSource;
import com.airbnb.spinaltap.mysql.MysqlSourceMetrics;
import com.airbnb.spinaltap.mysql.StateHistory;
import com.airbnb.spinaltap.mysql.StateRepository;
import com.airbnb.spinaltap.mysql.TableCache;
import com.airbnb.spinaltap.mysql.config.MysqlConfiguration;
import com.airbnb.spinaltap.mysql.exception.InvalidBinlogPositionException;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaManager;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.google.common.base.Preconditions;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a {@link MysqlSource} implement based on open-source library <a
 * href="https://github.com/shyiko/mysql-binlog-connector-java">.
 */
@Slf4j
public final class BinaryLogConnectorSource extends MysqlSource {
  private static final String INVALID_BINLOG_POSITION_ERROR_CODE = "1236";

  @NonNull private final BinaryLogClient binlogClient;
  @NonNull private final MysqlClient mysqlClient;
  private final String serverUUID;

  public BinaryLogConnectorSource(
      @NonNull final String name,
      @NonNull final MysqlConfiguration config,
      @NonNull final BinaryLogClient binlogClient,
      @NonNull final MysqlClient mysqlClient,
      @NonNull final TableCache tableCache,
      @NonNull final StateRepository stateRepository,
      @NonNull final StateHistory stateHistory,
      @NonNull final MysqlSchemaManager schemaManager,
      @NonNull final MysqlSourceMetrics metrics,
      @NonNull final AtomicLong currentLeaderEpoch) {
    super(
        name,
        new DataSource(config.getHost(), config.getPort(), name),
        new HashSet<>(config.getCanonicalTableNames()),
        tableCache,
        stateRepository,
        stateHistory,
        config.getInitialBinlogFilePosition(),
        schemaManager,
        metrics,
        currentLeaderEpoch,
        new AtomicReference<>(),
        new AtomicReference<>());

    this.binlogClient = binlogClient;
    this.mysqlClient = mysqlClient;
    this.serverUUID = mysqlClient.getServerUUID();
    initializeClient(config.getSocketTimeoutInSeconds());
  }

  /** Initializes the {@link BinaryLogClient}. */
  private void initializeClient(final int socketTimeoutInSeconds) {
    binlogClient.setThreadFactory(
        runnable ->
            new Thread(
                runnable,
                String.format(
                    "binlog-client-%s-%s-%d",
                    name, getDataSource().getHost(), getDataSource().getPort())));

    binlogClient.setSocketFactory(
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

    binlogClient.setKeepAlive(false);
    binlogClient.registerEventListener(new BinlogEventListener());
    binlogClient.registerLifecycleListener(new BinlogClientLifeCycleListener());
  }

  @Override
  protected void connect() throws Exception {
    binlogClient.connect();
  }

  @Override
  protected void disconnect() throws Exception {
    binlogClient.disconnect();
  }

  @Override
  protected boolean isConnected() {
    return binlogClient.isConnected();
  }

  @Override
  public void setPosition(@NonNull final BinlogFilePos pos) {
    if (!mysqlClient.isGtidModeEnabled()
        || (pos.getGtidSet() == null
            && pos != MysqlSource.EARLIEST_BINLOG_POS
            && pos != MysqlSource.LATEST_BINLOG_POS)) {
      log.info("Setting binlog position for source {} to {}", name, pos);

      binlogClient.setBinlogFilename(pos.getFileName());
      binlogClient.setBinlogPosition(pos.getNextPosition());
    } else {
      // GTID mode is enabled
      if (pos == MysqlSource.EARLIEST_BINLOG_POS) {
        log.info("Setting binlog position for source {} to earliest available GTIDSet", name);
        binlogClient.setGtidSet("");
        binlogClient.setGtidSetFallbackToPurged(true);
      } else if (pos == MysqlSource.LATEST_BINLOG_POS) {
        BinlogFilePos currentPos = mysqlClient.getMasterStatus();
        String gtidSet = currentPos.getGtidSet().toString();
        log.info("Setting binlog position for source {} to GTIDSet {}", name, gtidSet);
        binlogClient.setGtidSet(gtidSet);
      } else {
        String gtidSet = pos.getGtidSet().toString();
        log.info("Setting binlog position for source {} to GTIDSet {}", name, gtidSet);
        binlogClient.setGtidSet(gtidSet);
        if (serverUUID != null && serverUUID.equalsIgnoreCase(pos.getServerUUID())) {
          binlogClient.setBinlogFilename(pos.getFileName());
          binlogClient.setBinlogPosition(pos.getNextPosition());
          binlogClient.setUseBinlogFilenamePositionInGtidMode(true);
        }
      }
    }
  }

  private final class BinlogEventListener implements BinaryLogClient.EventListener {
    public void onEvent(Event event) {
      Preconditions.checkState(isStarted(), "Source is not started and should not process events");

      final EventHeaderV4 header = event.getHeader();
      final BinlogFilePos filePos =
          new BinlogFilePos(
              binlogClient.getBinlogFilename(),
              header.getPosition(),
              header.getNextPosition(),
              binlogClient.getGtidSet(),
              serverUUID);

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
