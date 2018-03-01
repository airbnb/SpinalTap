/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.source.Source;
import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.airbnb.spinaltap.common.validator.MutationOrderValidator;
import com.airbnb.spinaltap.mysql.config.MysqlSchemaStoreConfiguration;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.event.DeleteEvent;
import com.airbnb.spinaltap.mysql.event.QueryEvent;
import com.airbnb.spinaltap.mysql.event.StartEvent;
import com.airbnb.spinaltap.mysql.event.TableMapEvent;
import com.airbnb.spinaltap.mysql.event.UpdateEvent;
import com.airbnb.spinaltap.mysql.event.WriteEvent;
import com.airbnb.spinaltap.mysql.event.XidEvent;
import com.airbnb.spinaltap.mysql.exception.InvalidBinlogPositionException;
import com.airbnb.spinaltap.mysql.schema.CachedMysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.LatestMysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaDatabase;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaStore;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaStoreManager;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaTracker;
import com.airbnb.spinaltap.mysql.schema.MysqlSchemaUtil;
import com.airbnb.spinaltap.mysql.schema.MysqlTableSchema;
import com.airbnb.spinaltap.mysql.schema.SchemaStore;
import com.airbnb.spinaltap.mysql.schema.SchemaTracker;
import com.airbnb.spinaltap.mysql.validator.EventOrderValidator;
import com.airbnb.spinaltap.mysql.validator.MutationSchemaValidator;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;
import com.github.shyiko.mysql.binlog.event.XidEventData;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.net.Socket;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.skife.jdbi.v2.DBI;

@Slf4j
public class MysqlSource extends AbstractMysqlSource {
  private static final String INVALID_BINLOG_POSITION_ERROR_CODE = "1236";

  private final BinaryLogClient client;

  public static Source create(
      String name,
      String host,
      int port,
      int socketTimeoutInSeconds,
      String user,
      String password,
      long serverId,
      List<String> tableNames,
      Repository<SourceState> stateRepo,
      Repository<Collection<SourceState>> stateHistoryRepo,
      BinlogFilePos initialBinlogPosition,
      boolean isSchemaVersionEnabled,
      MysqlSchemaStoreConfiguration schemaStoreConfig,
      MysqlSourceMetrics metrics,
      long leaderEpoch) {
    DBI mysqlDBI = MysqlSchemaUtil.createMysqlDBI(host, port, user, password, null);

    BinaryLogClient client = new BinaryLogClient(host, port, user, password);
    // Set different server_id for staging and production environment.
    // Conflict occurs when more than one client of same server_id connect to a MySQL server.
    client.setServerId(serverId);

    DataSource dataSource = new DataSource(host, port, name);

    StateRepository stateRepository = new StateRepository(name, stateRepo, metrics);
    StateHistory stateHistory = new StateHistory(name, stateHistoryRepo, metrics);

    LatestMysqlSchemaStore schemaReader = new LatestMysqlSchemaStore(name, mysqlDBI, metrics);
    SchemaStore<MysqlTableSchema> schemaStore;
    SchemaTracker schemaTracker;

    if (isSchemaVersionEnabled) {
      DBI schemaStoreDBI =
          MysqlSchemaUtil.createMysqlDBI(
              schemaStoreConfig.getHost(),
              schemaStoreConfig.getPort(),
              user,
              password,
              schemaStoreConfig.getDatabase());
      MysqlSchemaStore mysqlSchemaStore =
          new MysqlSchemaStore(
              name, schemaStoreDBI, schemaStoreConfig.getArchiveDatabase(), metrics);

      DBI schemaDatabaseDBI =
          MysqlSchemaUtil.createMysqlDBI(
              schemaStoreConfig.getHost(), schemaStoreConfig.getPort(), user, password, null);
      MysqlSchemaDatabase schemaDatabase =
          new MysqlSchemaDatabase(name, schemaDatabaseDBI, metrics);

      if (!mysqlSchemaStore.isCreated()) {
        MysqlSchemaStoreManager schemaStoreManager =
            new MysqlSchemaStoreManager(name, schemaReader, mysqlSchemaStore, schemaDatabase);
        schemaStoreManager.bootstrapAll();
      }

      schemaStore = new CachedMysqlSchemaStore(name, mysqlSchemaStore, metrics);
      schemaTracker = new MysqlSchemaTracker(schemaStore, schemaDatabase);
    } else {
      schemaStore = schemaReader;
      schemaTracker = (event) -> {};
    }

    TableCache tableCache = new TableCache(schemaStore);

    MysqlSource source =
        new MysqlSource(
            name,
            dataSource,
            client,
            new HashSet<>(tableNames),
            tableCache,
            stateRepository,
            stateHistory,
            initialBinlogPosition,
            schemaTracker,
            metrics,
            new AtomicLong(leaderEpoch),
            socketTimeoutInSeconds);

    source.addEventValidator(new EventOrderValidator(metrics::outOfOrder));
    source.addMutationValidator(new MutationOrderValidator(metrics::outOfOrder));
    source.addMutationValidator(new MutationSchemaValidator(metrics::invalidSchema));

    return source;
  }

  MysqlSource(
      String name,
      DataSource dataSource,
      BinaryLogClient client,
      Set<String> tableNames,
      TableCache tableCache,
      StateRepository stateRepository,
      StateHistory stateHistory,
      BinlogFilePos initialBinlogFilePosition,
      SchemaTracker schemaTracker,
      MysqlSourceMetrics metrics,
      AtomicLong currentLeaderEpoch,
      int socketTimeoutInSeconds) {
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

  private void initializeClient(int socketTimeoutInSeconds) {
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
            Throwables.propagate(ex);
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
  public void setPosition(BinlogFilePos pos) {
    log.info("Setting binlog position for source {} to {}", name, pos);

    client.setBinlogFilename(pos.getFileName());
    client.setBinlogPosition(pos.getNextPosition());
  }

  public static BinlogEvent toBinlogEvent(Event event, BinlogFilePos filePos) {
    EventHeaderV4 header = event.getHeader();
    EventType eventType = header.getEventType();

    long serverId = header.getServerId();
    long timestamp = header.getTimestamp();

    if (EventType.isWrite(eventType)) {
      WriteRowsEventData data = event.getData();
      return new WriteEvent(data.getTableId(), serverId, timestamp, filePos, data.getRows());
    } else if (EventType.isUpdate(eventType)) {
      UpdateRowsEventData data = event.getData();
      return new UpdateEvent(data.getTableId(), serverId, timestamp, filePos, data.getRows());
    } else if (EventType.isDelete(eventType)) {
      DeleteRowsEventData data = event.getData();
      return new DeleteEvent(data.getTableId(), serverId, timestamp, filePos, data.getRows());
    } else {
      switch (eventType) {
        case TABLE_MAP:
          TableMapEventData tableMapData = event.getData();
          return new TableMapEvent(
              tableMapData.getTableId(),
              serverId,
              timestamp,
              filePos,
              tableMapData.getDatabase(),
              tableMapData.getTable(),
              tableMapData.getColumnTypes());
        case XID:
          XidEventData xidData = event.getData();
          return new XidEvent(serverId, timestamp, filePos, xidData.getXid());
        case QUERY:
          QueryEventData queryData = event.getData();
          return new QueryEvent(
              serverId, timestamp, filePos, queryData.getDatabase(), queryData.getSql());
        case FORMAT_DESCRIPTION:
          return new StartEvent(serverId, timestamp, filePos);
        default:
          return null;
      }
    }
  }

  class BinlogEventListener implements BinaryLogClient.EventListener {
    public void onEvent(Event event) {
      Preconditions.checkState(isStarted(), "Source is not started and should not process events");

      EventHeaderV4 header = event.getHeader();
      BinlogFilePos filePos =
          new BinlogFilePos(
              client.getBinlogFilename(), header.getPosition(), header.getNextPosition());

      BinlogEvent binlogEvent = toBinlogEvent(event, filePos);
      if (binlogEvent != null) {
        processEvent(binlogEvent);
      }
    }
  }

  /**
   * Lifecycle listener methods are called synchronized in BinaryLogClient. We should not enter
   * critical sections in SpinalTap code path to avoid deadlocks
   */
  class BinlogClientLifeCycleListener implements BinaryLogClient.LifecycleListener {
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
