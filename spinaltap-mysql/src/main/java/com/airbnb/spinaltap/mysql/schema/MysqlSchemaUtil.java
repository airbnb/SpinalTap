/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.airbnb.spinaltap.mysql.BinlogFilePos;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.joda.time.DateTimeConstants;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

@Slf4j
@UtilityClass
public class MysqlSchemaUtil {
  static final Retryer<Void> VOID_RETRYER = createRetryer();
  static final Retryer<Boolean> BOOLEAN_RETRYER = createRetryer();
  static final Retryer<String> STRING_RETRYER = createRetryer();
  static final Retryer<Integer> INTEGER_RETRYER = createRetryer();
  static final Retryer<List<ColumnInfo>> LIST_COLUMNINFO_RETRYER = createRetryer();
  static final Retryer<List<String>> LIST_STRING_RETRYER = createRetryer();
  static final ColumnMapper COLUMN_MAPPER = new ColumnMapper();

  static void executeSQL(
      @NotNull final Handle handle, final String database, @NotNull final String sql)
      throws SQLException {
    // Use JDBC API to excute raw SQL without any return value and no binding in SQL statement, so
    // we don't need to escape colon(:)
    // SQL statement with colon(:) inside needs to be escaped if using JDBI Handle.execute(sql)
    Connection connection = handle.getConnection();
    if (database != null) {
      connection.setCatalog(database);
    }
    Statement statement = connection.createStatement();
    statement.execute(sql);
  }

  public static DBI createMysqlDBI(
      @NotNull final String host,
      @Min(0) final int port,
      @NotNull final String user,
      @NotNull final String password,
      final String database) {
    final MysqlDataSource dataSource = new MysqlConnectionPoolDataSource();

    dataSource.setUser(user);
    dataSource.setPassword(password);
    dataSource.setServerName(host);
    dataSource.setPort(port);
    dataSource.setJdbcCompliantTruncation(false);
    dataSource.setAutoReconnectForConnectionPools(true);
    if (database != null) {
      dataSource.setDatabaseName(database);
    }

    return new DBI(dataSource);
  }

  private static <T> Retryer<T> createRetryer() {
    return RetryerBuilder.<T>newBuilder()
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.exponentialWait(2, 30, TimeUnit.SECONDS))
        .withStopStrategy(StopStrategies.stopAfterDelay(5 * DateTimeConstants.MILLIS_PER_MINUTE))
        .build();
  }

  public static MysqlTableSchema createTableSchema(
      @NotNull final String source,
      @NotNull final String database,
      @NotNull final String table,
      @NotNull final String sql,
      @NotNull final List<ColumnInfo> columnInfoList) {
    return new MysqlTableSchema(
        0, source, database, table, new BinlogFilePos(0), sql, 0, columnInfoList, null);
  }

  static String escapeBackQuote(@NotNull final String name) {
    // MySQL allows backquote in database/table name, but need to escape it in DDL
    return name.replace("`", "``");
  }

  static class ColumnMapper implements ResultSetMapper<ColumnInfo> {
    public ColumnInfo map(int index, ResultSet resultSet, StatementContext context)
        throws SQLException {
      final String table = resultSet.getString("TABLE_NAME");
      final String name = resultSet.getString("COLUMN_NAME");
      final String key = resultSet.getString("COLUMN_KEY");
      final String type = resultSet.getString("COLUMN_TYPE");

      log.debug(
          "Mapping column with table {}, name {}, key {} and column type {}",
          table,
          name,
          key,
          type);
      return new ColumnInfo(table, name, type, isPrimary(key));
    }

    private static boolean isPrimary(String columnKey) {
      return "PRI".equals(columnKey);
    }
  }
}
