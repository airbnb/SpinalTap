/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.pipe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class PipeManagerTest {
  private static final String NAME = "test";
  private static final String PARTITION = "test_0";

  private final Pipe firstPipe = mock(Pipe.class);
  private final Pipe secondPipe = mock(Pipe.class);

  @Test
  public void testAddRemovePipe() throws Exception {
    PipeManager pipeManager = new PipeManager();

    pipeManager.addPipes(NAME, PARTITION, ImmutableList.of(firstPipe, secondPipe));

    verify(firstPipe, times(1)).start();
    verify(secondPipe, times(1)).start();

    pipeManager.removePipe(NAME, PARTITION);

    verify(firstPipe, times(1)).stop();
    verify(secondPipe, times(1)).stop();

    assertTrue(pipeManager.isEmpty());
  }
}
