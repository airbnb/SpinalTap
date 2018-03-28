/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.DestinationException;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AbstractDestinationTest {
  private final Destination.Listener listener = mock(Destination.Listener.class);
  private final DestinationMetrics metrics = mock(DestinationMetrics.class);

  private final Mutation<?> firstMutation = mock(Mutation.class);
  private final Mutation<?> secondMutation = mock(Mutation.class);
  private final Mutation<?> thirdMutation = mock(Mutation.class);

  private final List<Mutation<?>> mutations =
      ImmutableList.of(firstMutation, secondMutation, thirdMutation);

  private TestDestination destination;

  @Before
  public void setUp() throws Exception {
    destination = new TestDestination();
    destination.addListener(listener);
  }

  @Test
  public void testSend() throws Exception {
    Mutation.Metadata metadata = mock(Mutation.Metadata.class);
    when(firstMutation.getMetadata()).thenReturn(metadata);
    when(secondMutation.getMetadata()).thenReturn(metadata);
    when(thirdMutation.getMetadata()).thenReturn(metadata);
    when(metadata.getTimestamp()).thenReturn(0L);

    destination.send(mutations);

    assertEquals(3, destination.getPublishedMutations());
    assertEquals(thirdMutation, destination.getLastPublishedMutation());

    verify(metrics).publishSucceeded(mutations);
    verify(listener).onSend(mutations);
  }

  @Test
  public void testSendEmptyMutationList() throws Exception {
    destination.send(ImmutableList.of());

    assertEquals(0, destination.getPublishedMutations());
    assertNull(destination.getLastPublishedMutation());
    verifyZeroInteractions(metrics);
  }

  @Test(expected = DestinationException.class)
  public void testSendFailure() throws Exception {
    Mutation.Metadata metadata = mock(Mutation.Metadata.class);
    when(firstMutation.getMetadata()).thenReturn(metadata);
    when(metadata.getTimestamp()).thenReturn(0L);

    destination.setFailPublish(true);

    try {
      destination.send(ImmutableList.of(firstMutation, secondMutation));
    } catch (Exception ex) {
      assertNull(destination.getLastPublishedMutation());
      verify(metrics, times(2))
          .publishFailed(any(Mutation.class), any(RuntimeException.class));

      throw ex;
    }
  }

  @Test
  public void testOpen() throws Exception {
    Mutation.Metadata metadata = mock(Mutation.Metadata.class);
    when(firstMutation.getMetadata()).thenReturn(metadata);
    when(metadata.getTimestamp()).thenReturn(0L);

    destination.send(ImmutableList.of(firstMutation));

    assertEquals(firstMutation, destination.getLastPublishedMutation());

    destination.open();

    assertNull(destination.getLastPublishedMutation());
    verify(listener, times(1)).onStart();
  }

  class TestDestination extends AbstractDestination<Mutation<?>> {
    @Getter private int publishedMutations;
    @Setter private boolean failPublish;

    public TestDestination() {
      super(m -> m, metrics, 0L);
    }

    @Override
    public boolean isStarted() {
      return true;
    }

    @VisibleForTesting
    @Override
    public void publish(List<Mutation<?>> MUTATIONS) {
      if (failPublish) {
        throw new RuntimeException();
      }

      publishedMutations += MUTATIONS.size();
    }
  }
}
