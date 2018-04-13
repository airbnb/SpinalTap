/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.Min;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/** Utility methods for concurrency operations */
@UtilityClass
public class ConcurrencyUtil {
  /**
   * Attempts to shutdown the {@link ExecutorService}. If the service does not terminate within the
   * specified timeout, a force shutdown will be triggered.
   *
   * @param executorService the {@link ExecutorService}.
   * @param timeout the timeout.
   * @param unit the time unit.
   * @return {@code true} if shutdown was successful within the specified timeout, {@code false}
   *     otherwise.
   */
  public boolean shutdownGracefully(
      @NonNull ExecutorService executorService, @Min(1) long timeout, @NonNull TimeUnit unit) {
    boolean shutdown = false;
    executorService.shutdown();
    try {
      shutdown = executorService.awaitTermination(timeout, unit);
    } catch (InterruptedException e) {
      executorService.shutdownNow();
    }
    if (!shutdown) {
      executorService.shutdownNow();
    }
    return shutdown;
  }
}
