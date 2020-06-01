/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql.schema;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.joda.time.DateTimeConstants;

@Slf4j
@UtilityClass
public class MysqlSchemaUtil {
  public final Retryer<Void> VOID_RETRYER = createRetryer();
  public final Retryer<List<MysqlColumn>> LIST_COLUMN_RETRYER = createRetryer();
  public final Retryer<List<MysqlTableSchema>> LIST_TABLE_SCHEMA_RETRYER = createRetryer();
  public final Retryer<List<String>> LIST_STRING_RETRYER = createRetryer();

  public void executeWithJdbc(
      @NonNull final Handle handle, final String database, @NonNull final String sql)
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

  private <T> Retryer<T> createRetryer() {
    return RetryerBuilder.<T>newBuilder()
        .retryIfRuntimeException()
        .withWaitStrategy(WaitStrategies.exponentialWait(2, 30, TimeUnit.SECONDS))
        .withStopStrategy(StopStrategies.stopAfterDelay(3 * DateTimeConstants.MILLIS_PER_MINUTE))
        .build();
  }

  public String escapeBackQuote(@NonNull final String name) {
    // MySQL allows backquote in database/table name, but need to escape it in DDL
    return name.replace("`", "``");
  }

  String removeCommentsFromDDL(final String ddl) {
    return ddl
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
        .replaceAll("^\\s+", "");
  }
}
