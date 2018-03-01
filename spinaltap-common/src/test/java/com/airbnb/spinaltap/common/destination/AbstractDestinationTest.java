/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.DestinationException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Test;

public class AbstractDestinationTest {
  private final Destination.Listener listener = mock(Destination.Listener.class);
  private final DestinationMetrics metrics = mock(DestinationMetrics.class);

  TestDestination destination;

  @Before
  public void setUp() throws Exception {
    destination = new TestDestination();
    destination.addListener(listener);
  }

  @Test
  public void testSend() throws Exception {
    Mutation<?> firstMutation = mock(Mutation.class);
    Mutation<?> secondMutation = mock(Mutation.class);
    Mutation<?> thirdMutation = mock(Mutation.class);

    List<Mutation<?>> mutations = ImmutableList.of(firstMutation, secondMutation, thirdMutation);

    destination.send(mutations);

    assertEquals(3, destination.getPublishedMutations());
    assertEquals(thirdMutation, destination.getLastPublishedMutation());

    verify(metrics, times(1)).publishSucceeded(mutations);
    verify(listener, times(1)).onSend(mutations);
  }

  @Test
  public void testSendEmptyMutationList() throws Exception {
    destination.send(Lists.newArrayList());

    assertEquals(0, destination.getPublishedMutations());
    assertNull(destination.getLastPublishedMutation());
    verifyZeroInteractions(metrics);
  }

  @Test(expected = DestinationException.class)
  public void testSendFailure() throws Exception {
    Mutation<?> mutation = mock(Mutation.class);

    destination.setFailPublish(true);

    try {
      destination.send(ImmutableList.of(mutation, mutation));
    } catch (Exception ex) {
      assertNull(destination.getLastPublishedMutation());
      verify(metrics, times(2)).publishFailed(any(Mutation.class), any(RuntimeException.class));

      throw ex;
    }
  }

  @Test
  public void testOpen() throws Exception {
    Mutation<?> mutation = mock(Mutation.class);

    destination.send(ImmutableList.of(mutation));

    assertEquals(mutation, destination.getLastPublishedMutation());

    destination.open();

    assertNull(destination.getLastPublishedMutation());
    verify(listener, times(1)).onStart();
  }

  class TestDestination extends AbstractDestination<Mutation<?>> {
    @Getter private int publishedMutations;
    @Setter private boolean failPublish;

    public TestDestination() {
      super(m -> m, metrics);
    }

    @Override
    public boolean isStarted() {
      return true;
    }

    @VisibleForTesting
    @Override
    public void publish(List<Mutation<?>> mutations) {
      if (failPublish) {
        throw new RuntimeException();
      }

      publishedMutations += mutations.size();
    }
  }
}
