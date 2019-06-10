/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airbnb.spinaltap.Mutation;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class BufferedDestinationTest {
  private final Mutation<?> firstMutation = mock(Mutation.class);
  private final Mutation<?> secondMutation = mock(Mutation.class);
  private final Mutation<?> thirdMutation = mock(Mutation.class);

  private final List<Mutation<?>> mutations =
      ImmutableList.of(firstMutation, secondMutation, thirdMutation);

  private final Destination destination = mock(Destination.class);
  private final Destination.Listener listener = mock(Destination.Listener.class);
  private final DestinationMetrics metrics = mock(DestinationMetrics.class);

  private BufferedDestination bufferedDestination =
      new BufferedDestination("test", 10, destination, metrics);

  @Before
  public void setUp() throws Exception {
    bufferedDestination.addListener(listener);
  }

  @Test
  public void testOpenClose() throws Exception {
    when(destination.isStarted()).thenReturn(false);

    bufferedDestination.open();

    when(destination.isStarted()).thenReturn(true);

    assertTrue(bufferedDestination.isStarted());
    verify(destination).open();

    bufferedDestination.open();

    assertTrue(bufferedDestination.isStarted());
    verify(destination).open();

    bufferedDestination.close();

    verify(destination).close();
  }

  @Test
  public void testSend() throws Exception {
    when(destination.isStarted()).thenReturn(true);

    bufferedDestination.send(ImmutableList.of(firstMutation, secondMutation));
    bufferedDestination.processMutations();

    verify(destination).send(ImmutableList.of(firstMutation, secondMutation));

    bufferedDestination.send(ImmutableList.of(firstMutation));
    bufferedDestination.send(ImmutableList.of(secondMutation, thirdMutation));
    bufferedDestination.processMutations();

    verify(destination).send(mutations);
  }
}
