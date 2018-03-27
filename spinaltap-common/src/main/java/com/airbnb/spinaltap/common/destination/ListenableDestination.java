/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Base {@link Destination} implement using <a href="https://en.wikipedia.org/wiki/Observer_pattern">observer pattern</a>
 * to allow listening to streamed events and subscribe to lifecycle change notifications.
 */
abstract class ListenableDestination implements Destination {
  private final List<Listener> listeners = new ArrayList<>();

  @Override
  public void addListener(@NonNull final Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(@NonNull final Listener listener) {
    listeners.remove(listener);
  }

  protected void notifyStart() {
    listeners.forEach(Destination.Listener::onStart);
  }

  protected void notifySend(final List<? extends Mutation<?>> mutations) {
    listeners.forEach(listener -> listener.onSend(mutations));
  }

  protected void notifyError(final Exception ex) {
    listeners.forEach(listener -> listener.onError(ex));
  }

  @Override
  public void open() {
    notifyStart();
  }
}
