/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.Mutation;
import java.util.ArrayList;
import java.util.List;

abstract class ListenableSource<E extends SourceEvent> implements Source {
  private final List<Listener> listeners = new ArrayList<>();

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  protected void notifyMutations(List<? extends Mutation<?>> mutations) {
    if (!mutations.isEmpty()) {
      listeners.forEach(listener -> listener.onMutation(mutations));
    }
  }

  protected void notifyEvent(E event) {
    listeners.forEach(listener -> listener.onEvent(event));
  }

  protected void notifyError(Throwable error) {
    listeners.forEach(listener -> listener.onError(error));
  }

  protected void notifyStart() {
    listeners.forEach(Source.Listener::onStart);
  }
}
