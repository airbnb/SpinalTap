/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.Mutation;
import java.util.List;

/** Produces events and notifies of mutations */
public interface Source {
  String getName();

  void addListener(Listener listener);

  void removeListener(Listener listener);

  boolean isStarted();

  void open();

  void close();

  void clear();

  void checkpoint(Mutation<?> mutation);

  abstract class Listener {
    public void onStart() {}

    public void onEvent(SourceEvent event) {}

    public void onMutation(List<? extends Mutation<?>> mutations) {}

    public void onError(Throwable error) {}
  }
}
