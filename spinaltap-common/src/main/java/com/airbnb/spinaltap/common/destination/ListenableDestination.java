/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import java.util.ArrayList;
import java.util.List;

abstract class ListenableDestination implements Destination {
  private final List<Listener> listeners = new ArrayList<>();

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  protected void notifyStart() {
    listeners.forEach(Destination.Listener::onStart);
  }

  protected void notifySend(List<? extends Mutation<?>> mutations) {
    listeners.forEach(listener -> listener.onSend(mutations));
  }

  protected void notifyError(Exception ex) {
    listeners.forEach(listener -> listener.onError(ex));
  }

  @Override
  public void open() {
    notifyStart();
  }
}
