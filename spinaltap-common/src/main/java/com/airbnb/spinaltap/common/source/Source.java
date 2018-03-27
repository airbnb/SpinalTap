/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.destination.Destination;

import java.util.List;

/**
 *  Represents the originating end of the {@link com.airbnb.spinaltap.common.pipe.Pipe}, where
 * {@link SourceEvent}s are received and transformed to {@link Mutation}s.
 */
public interface Source {
  /**
   * The name of the source
   */
  String getName();

  /**
   * Adds a {@link Listener} to the source.
   */
  void addListener(Listener listener);

  /**
   * Removes a {@link Listener} from the source.
   */
  void removeListener(Listener listener);

  /**
   * Whether the source is started and processing events.
   */
  boolean isStarted();

  /**
   * Initializes the source and prepares for event processing.
   *
   * <p>The operation should be idempotent.</p>
   */
  void open();

  /**
   * Stops event processing and closes the source.
   *
   * <p>The operation should be idempotent.</p>
   */
  void close();

  /**
   * Clears the state of the source.
   *
   * <p>The operation should be idempotent.</p>
   */
  void clear();

  /**
   * Commits the source checkpoint on the specified {@link Mutation}. On source start, streaming
   * will begin from the last marked checkpoint.
   */
  void checkpoint(Mutation<?> mutation);

  /**
   * Represents a source listener to get notified of events and lifecycle changes.
   */
  abstract class Listener {
    /**
     * Action to perform after the {@link Destination} has started.
     */
    public void onStart() {}

    /**
     * Action to perform after a {@link SourceEvent}s has been received.
     */
    public void onEvent(SourceEvent event) {}

    /**
     * Action to perform after a {@link Mutation}s has been detected.
     */
    public void onMutation(List<? extends Mutation<?>> mutations) {}

    /**
     * Action to perform when an error is caught on processing an event.
     */
    public void onError(Throwable error) {}
  }
}
