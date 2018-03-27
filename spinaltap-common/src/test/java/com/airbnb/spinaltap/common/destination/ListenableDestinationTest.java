package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ListenableDestinationTest {
  private final Destination.Listener listener = mock(Destination.Listener.class);

  private ListenableDestination destination = new TestListenableDestination();

  @Test
  public void test() throws Exception {
    Exception exception = mock(Exception.class);
    List<Mutation<?>> mutations = ImmutableList.of(mock(Mutation.class));

    destination.addListener(listener);

    destination.notifyStart();
    destination.notifySend(mutations);
    destination.notifyError(exception);

    verify(listener).onStart();
    verify(listener).onSend(mutations);
    verify(listener).onError(exception);

    destination.removeListener(listener);

    destination.notifyStart();
    destination.notifySend(mutations);
    destination.notifyError(exception);

    verifyNoMoreInteractions(listener);
  }

  private static final class TestListenableDestination extends ListenableDestination {
    @Override
    public Mutation<?> getLastPublishedMutation() {
      return null;
    }

    @Override
    public void send(List<? extends Mutation<?>> mutations) {
    }

    @Override
    public boolean isStarted() {
      return false;
    }

    @Override
    public void close() {
    }

    @Override
    public void clear() {
    }
  }
}
