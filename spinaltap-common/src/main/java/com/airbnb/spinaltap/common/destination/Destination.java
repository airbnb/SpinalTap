/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import java.util.Collections;
import java.util.List;

/** Other end of a pipe which emits Mutation data to destinations */
public interface Destination {
  Mutation<?> getLastPublishedMutation();

  default void send(Mutation<?> mutation) {
    send(Collections.singletonList(mutation));
  }

  void send(List<? extends Mutation<?>> mutations);

  void addListener(Listener listener);

  void removeListener(Listener listener);

  boolean isStarted();

  void open();

  void close();

  void clear();

  abstract class Listener {
    public void onStart() {}

    public void onSend(List<? extends Mutation<?>> mutations) {}

    public void onError(Exception ex) {}
  }
}
