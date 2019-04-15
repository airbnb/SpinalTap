/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.pipe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.destination.Destination;
import com.airbnb.spinaltap.common.source.Source;
import org.junit.Before;
import org.junit.Test;

public class PipeTest {
  private final Source source = mock(Source.class);
  private final Destination destination = mock(Destination.class);
  private final PipeMetrics metrics = mock(PipeMetrics.class);
  private final Mutation lastMutation = mock(Mutation.class);

  private final Pipe pipe = new Pipe(source, destination, metrics);

  @Before
  public void setUp() throws Exception {
    when(destination.getLastPublishedMutation()).thenReturn(lastMutation);
  }

  @Test
  public void testStartStop() throws Exception {
    Mutation mutation = mock(Mutation.class);
    Mutation.Metadata metadata = mock(Mutation.Metadata.class);

    when(destination.getLastPublishedMutation()).thenReturn(mutation);
    when(mutation.getMetadata()).thenReturn(metadata);

    pipe.start();

    verify(source, times(1)).addListener(any(Source.Listener.class));
    verify(source, times(1)).open();

    verify(destination, times(1)).addListener(any(Destination.Listener.class));
    verify(destination, times(1)).open();

    verify(metrics, times(1)).open();

    pipe.stop();

    verify(source, times(1)).removeListener(any(Source.Listener.class));
    verify(source, times(1)).checkpoint(mutation);
    verify(source, times(1)).close();

    verify(destination, times(1)).removeListener(any(Destination.Listener.class));
    verify(destination, times(1)).close();

    verify(metrics, times(1)).close();
  }

  @Test
  public void testIsStarted() throws Exception {
    when(source.isStarted()).thenReturn(true);
    when(destination.isStarted()).thenReturn(false);

    assertFalse(pipe.isStarted());

    when(source.isStarted()).thenReturn(false);
    when(destination.isStarted()).thenReturn(true);

    assertFalse(pipe.isStarted());

    when(source.isStarted()).thenReturn(true);
    when(destination.isStarted()).thenReturn(true);

    assertTrue(pipe.isStarted());
  }

  @Test
  public void testCheckpoint() throws Exception {
    pipe.checkpoint();

    verify(source, times(1)).checkpoint(lastMutation);
  }
}
