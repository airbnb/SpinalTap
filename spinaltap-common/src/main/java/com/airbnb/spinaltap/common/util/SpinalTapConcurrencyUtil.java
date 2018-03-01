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

@UtilityClass
public final class SpinalTapConcurrencyUtil {
  public static boolean shutdownGracefully(
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
