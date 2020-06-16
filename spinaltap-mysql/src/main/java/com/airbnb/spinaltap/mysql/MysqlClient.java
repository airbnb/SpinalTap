/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import com.airbnb.spinaltap.common.config.TlsConfiguration;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

/** Represents a MySQL server connection and context with utility functions */
@Slf4j
@RequiredArgsConstructor
@Getter
public class MysqlClient {
  private final Jdbi jdbi;

  public static MysqlClient create(
      String host,
      int port,
      String user,
      String password,
      boolean mTlsEnabled,
      TlsConfiguration tlsConfig) {
    return new MysqlClient(
        Jdbi.create(createMysqlDataSource(host, port, user, password, mTlsEnabled, tlsConfig)));
  }

  public static MysqlDataSource createMysqlDataSource(
      String host,
      int port,
      String user,
      String password,
      boolean mTlsEnabled,
      TlsConfiguration tlsConfig) {
    MysqlDataSource dataSource = new MysqlConnectionPoolDataSource();

    dataSource.setUser(user);
    dataSource.setPassword(password);
    dataSource.setServerName(host);
    dataSource.setPort(port);
    dataSource.setJdbcCompliantTruncation(false);
    dataSource.setAutoReconnectForConnectionPools(true);

    if (mTlsEnabled && tlsConfig != null) {
      dataSource.setUseSSL(true);
      if (tlsConfig.getKeyStoreFilePath() != null && tlsConfig.getKeyStorePassword() != null) {
        dataSource.setClientCertificateKeyStoreUrl("file:" + tlsConfig.getKeyStoreFilePath());
        dataSource.setClientCertificateKeyStorePassword(tlsConfig.getKeyStorePassword());
      }
      if (tlsConfig.getKeyStoreType() != null) {
        dataSource.setClientCertificateKeyStoreType(tlsConfig.getKeyStoreType());
      }
      if (tlsConfig.getTrustStoreFilePath() != null && tlsConfig.getTrustStorePassword() != null) {
        dataSource.setTrustCertificateKeyStoreUrl("file:" + tlsConfig.getTrustStoreFilePath());
        dataSource.setTrustCertificateKeyStorePassword(tlsConfig.getTrustStorePassword());
      }
      if (tlsConfig.getTrustStoreType() != null) {
        dataSource.setTrustCertificateKeyStoreType(tlsConfig.getTrustStoreType());
      }
    }

    return dataSource;
  }

  public BinlogFilePos getMasterStatus() {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("SHOW MASTER STATUS")
                .map(
                    (rs, ctx) -> {
                      BinlogFilePos.Builder builder =
                          BinlogFilePos.builder()
                              .withServerUUID(getServerUUID())
                              .withFileName(rs.getString(1))
                              .withPosition(rs.getLong(2))
                              .withNextPosition(rs.getLong(2));

                      if (rs.getMetaData().getColumnCount() > 4) {
                        builder.withGtidSet(rs.getString(5));
                      }
                      return builder.build();
                    })
                .findFirst()
                .orElse(null));
  }

  public String getServerUUID() {
    return getGlobalVariableValue("server_uuid");
  }

  public boolean isGtidModeEnabled() {
    return "ON".equalsIgnoreCase(getGlobalVariableValue("gtid_mode"));
  }

  public List<String> getBinaryLogs() {
    return jdbi.withHandle(
        handle -> handle.createQuery("SHOW BINARY LOGS").map((rs, ctx) -> rs.getString(1)).list());
  }

  public String getGlobalVariableValue(String variableName) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    String.format("SHOW GLOBAL VARIABLES WHERE Variable_name = '%s'", variableName))
                .map((rs, ctx) -> rs.getString(2))
                .findFirst()
                .orElse(null));
  }
}
