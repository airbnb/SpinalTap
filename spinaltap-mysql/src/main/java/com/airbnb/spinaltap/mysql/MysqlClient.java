/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/** Represents a MySQL server connection and context with utility functions */
@Slf4j
public class MysqlClient {
  private final String host;
  private final int port;
  private final String user;
  private final String password;
  private final MysqlDataSource dataSource;

  public MysqlClient(String host, int port, String user, String password) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    dataSource = createMysqlDataSource();
  }

  public BinlogFilePos getMasterStatus() {
    AtomicReference<BinlogFilePos> binlogFilePos = new AtomicReference<>();
    String serverUUID = getServerUUID();
    try {
      executeQuery(
          "SHOW MASTER STATUS",
          rs -> {
            if (rs.next()) {
              BinlogFilePos.Builder builder =
                  BinlogFilePos.builder()
                      .withServerUUID(getServerUUID())
                      .withFileName(rs.getString(1))
                      .withPosition(rs.getLong(2))
                      .withNextPosition(rs.getLong(2));

              if (rs.getMetaData().getColumnCount() > 4) {
                builder.withGtidSet(rs.getString(5));
              }

              binlogFilePos.set(builder.build());
            }
          });
    } catch (SQLException ex) {
      log.error(String.format("Failed to execute SHOW MASTER STATUS on %s%d", host, port));
      throw new RuntimeException(ex);
    }
    return binlogFilePos.get();
  }

  public String getServerUUID() {
    return getGlobalVariableValue("server_uuid");
  }

  public boolean isGtidModeEnabled() {
    return "ON".equalsIgnoreCase(getGlobalVariableValue("gtid_mode"));
  }

  public String getGlobalVariableValue(String variableName) {
    AtomicReference<String> value = new AtomicReference<>();
    try {
      executeQuery(
          String.format("SHOW GLOBAL VARIABLES WHERE Variable_name = '%s'", variableName),
          rs -> {
            if (rs.next()) {
              value.set(rs.getString(2));
            }
          });
    } catch (SQLException ex) {
      log.error(
          String.format(
              "Failed to get global variable value for %s on %s:%d", variableName, host, port));
      throw new RuntimeException(ex);
    }
    return value.get();
  }

  private MysqlDataSource createMysqlDataSource() {
    MysqlDataSource dataSource = new MysqlConnectionPoolDataSource();

    dataSource.setUser(user);
    dataSource.setPassword(password);
    dataSource.setServerName(host);
    dataSource.setPort(port);
    dataSource.setJdbcCompliantTruncation(false);
    dataSource.setAutoReconnectForConnectionPools(true);
    return dataSource;
  }

  public void executeQuery(String sql, ResultSetConsumer resultSetConsumer) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      try (Statement statement = conn.createStatement()) {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
          if (resultSetConsumer != null) {
            resultSetConsumer.accept(resultSet);
          }
        }
      }
    }
  }

  public interface ResultSetConsumer {
    void accept(ResultSet rs) throws SQLException;
  }
}
