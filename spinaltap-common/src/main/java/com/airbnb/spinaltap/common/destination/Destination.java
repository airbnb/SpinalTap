/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import java.util.Collections;
import java.util.List;

/**
 * Represents the receiving end of the {@link com.airbnb.spinaltap.common.pipe.Pipe}, where {@link
 * Mutation}s are published, i.e. a Sink.
 */
public interface Destination {
  /** @return the last {@link Mutation} that has been successfully published. */
  Mutation<?> getLastPublishedMutation();

  default void send(Mutation<?> mutation) {
    send(Collections.singletonList(mutation));
  }

  /**
   * Publishes a list of {@link Mutation}s.
   *
   * <p>Note: On failure, streaming should be halted and the error propagated to avoid potential.
   * event loss
   */
  void send(List<? extends Mutation<?>> mutations);

  /** Adds a {@link Listener} to the destination. */
  void addListener(Listener listener);

  /** Removes a {@link Listener} from the destination. */
  void removeListener(Listener listener);

  /** @return whether the destination is started and publishing {@link Mutation}s */
  boolean isStarted();

  /**
   * Initializes the destination and prepares for {@link Mutation} publishing.
   *
   * <p>The operation should be idempotent.
   */
  void open();

  /**
   * Stops {@link Mutation} publishing and closes the destination.
   *
   * <p>The operation should be idempotent.
   */
  void close();

  /**
   * Clears the state of the destination.
   *
   * <p>The operation should be idempotent.
   */
  void clear();

  /** Represents a destination listener to get notified of events and lifecycle changes. */
  abstract class Listener {
    /** Action to perform after the {@link Destination} has started. */
    public void onStart() {}

    /** Action to perform after a list of {@link Mutation}s has been published. */
    public void onSend(List<? extends Mutation<?>> mutations) {}

    /** Action to perform when an error is caught on send. */
    public void onError(Exception ex) {}
  }
}
