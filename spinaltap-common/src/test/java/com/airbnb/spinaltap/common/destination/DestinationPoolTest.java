/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.KeyProvider;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NoArgsConstructor;
import org.junit.Test;

public class DestinationPoolTest {
  private final Destination firstDestination = mock(Destination.class);
  private final Destination secondDestination = mock(Destination.class);
  private final Destination thirdDestination = mock(Destination.class);
  private final Destination fourthDestination = mock(Destination.class);

  private final KeyProvider<Mutation<?>, String> keyProvider = mock(KeyProvider.class);
  private final List<Destination> destinations =
      Arrays.asList(firstDestination, secondDestination, thirdDestination, fourthDestination);

  private final DestinationPool destinationPool = new DestinationPool(keyProvider, destinations);

  @Test
  public void testOpenClose() throws Exception {
    Destination destination1 = new TestDestination();
    Destination destination2 = new TestDestination();

    DestinationPool testDestinationPool =
        new DestinationPool(keyProvider, ImmutableList.of(destination1, destination2));

    testDestinationPool.open();

    assertTrue(testDestinationPool.isStarted());
    assertTrue(destination1.isStarted());
    assertTrue(destination2.isStarted());

    testDestinationPool.close();

    assertFalse(testDestinationPool.isStarted());
    assertFalse(destination1.isStarted());
    assertFalse(destination2.isStarted());
  }

  @Test
  public void testIsOpen() throws Exception {
    when(firstDestination.isStarted()).thenReturn(true);
    when(secondDestination.isStarted()).thenReturn(true);
    when(thirdDestination.isStarted()).thenReturn(true);
    when(fourthDestination.isStarted()).thenReturn(false);

    assertFalse(destinationPool.isStarted());

    when(fourthDestination.isStarted()).thenReturn(true);

    assertTrue(destinationPool.isStarted());
  }

  @Test
  public void testSend() throws Exception {
    Mutation<?> firstMutation = mock(Mutation.class);
    Mutation<?> secondMutation = mock(Mutation.class);
    Mutation<?> thirdMutation = mock(Mutation.class);
    Mutation<?> fourthMutation = mock(Mutation.class);
    Mutation<?> fifthMutation = mock(Mutation.class);

    Mutation.Metadata firstMetadata = mock(Mutation.Metadata.class);
    Mutation.Metadata secondMetadata = mock(Mutation.Metadata.class);
    Mutation.Metadata thirdMetadata = mock(Mutation.Metadata.class);

    when(firstMetadata.getId()).thenReturn(3L);
    when(secondMetadata.getId()).thenReturn(2L);
    when(thirdMetadata.getId()).thenReturn(4L);

    when(firstMutation.getMetadata()).thenReturn(firstMetadata);
    when(secondMutation.getMetadata()).thenReturn(secondMetadata);
    when(thirdMutation.getMetadata()).thenReturn(thirdMetadata);

    when(keyProvider.get(firstMutation)).thenReturn("4");
    when(keyProvider.get(secondMutation)).thenReturn("6");
    when(keyProvider.get(thirdMutation)).thenReturn("9");
    when(keyProvider.get(fourthMutation)).thenReturn("2");
    when(keyProvider.get(fifthMutation)).thenReturn("1");

    assertEquals(null, firstDestination.getLastPublishedMutation());

    destinationPool.send(
        Arrays.asList(firstMutation, secondMutation, thirdMutation, fourthMutation, fifthMutation));

    verify(firstDestination, times(1)).send(Collections.singletonList(firstMutation));
    verify(secondDestination, times(1)).send(ImmutableList.of(thirdMutation, fifthMutation));
    verify(thirdDestination, times(1)).send(ImmutableList.of(secondMutation, fourthMutation));

    when(firstDestination.getLastPublishedMutation()).thenReturn((Mutation) firstMutation);
    when(secondDestination.getLastPublishedMutation()).thenReturn((Mutation) thirdMutation);
    when(thirdDestination.getLastPublishedMutation()).thenReturn((Mutation) secondMutation);
    when(fourthDestination.getLastPublishedMutation()).thenReturn(null);

    assertEquals(secondMutation, destinationPool.getLastPublishedMutation());

    when(firstDestination.getLastPublishedMutation()).thenReturn(null);

    assertEquals(null, destinationPool.getLastPublishedMutation());
  }

  @Test
  public void testSendFailure() throws Exception {
    Destination corruptDestination = new TestDestination();

    Mutation<?> firstMutation = mock(Mutation.class);
    Mutation<?> secondMutation = mock(Mutation.class);

    when(keyProvider.get(firstMutation)).thenReturn("0");
    when(keyProvider.get(secondMutation)).thenReturn("1");

    Destination.Listener listener = mock(Destination.Listener.class);
    DestinationPool pool =
        new DestinationPool(
            keyProvider, Arrays.asList(mock(Destination.class), corruptDestination));

    pool.addListener(listener);
    pool.send(ImmutableList.of(firstMutation, secondMutation));

    verify(listener, times(1)).onError(any(RuntimeException.class));
  }

  @NoArgsConstructor
  class TestDestination extends ListenableDestination {
    private AtomicBoolean isStarted = new AtomicBoolean();
    private AtomicBoolean isCleared = new AtomicBoolean();

    public Mutation<?> getLastPublishedMutation() {
      return null;
    }

    @Override
    public void send(List<? extends Mutation<?>> mutations) {
      notifyError(new RuntimeException());
    }

    @Override
    public boolean isStarted() {
      return isStarted.get();
    }

    @Override
    public void open() {
      isStarted.set(true);
    }

    @Override
    public void close() {
      isStarted.set(false);
    }

    @Override
    public void clear() {
      isCleared.set(true);
    }
  }
}
