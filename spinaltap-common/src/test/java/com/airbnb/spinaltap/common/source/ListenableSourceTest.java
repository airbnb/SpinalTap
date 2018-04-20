/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.airbnb.spinaltap.Mutation;
import org.junit.Test;

public class ListenableSourceTest {
  private final Source.Listener listener = mock(Source.Listener.class);

  private ListenableSource<SourceEvent> source = new TestListenableSource();

  @Test
  public void test() throws Exception {
    Exception exception = mock(Exception.class);
    SourceEvent event = mock(SourceEvent.class);

    source.addListener(listener);

    source.notifyStart();
    source.notifyEvent(event);
    source.notifyError(exception);

    verify(listener).onStart();
    verify(listener).onEvent(event);
    verify(listener).onError(exception);

    source.removeListener(listener);

    source.notifyStart();
    source.notifyEvent(event);
    source.notifyError(exception);

    verifyNoMoreInteractions(listener);
  }

  private static final class TestListenableSource extends ListenableSource<SourceEvent> {
    @Override
    public String getName() {
      return null;
    }

    @Override
    public boolean isStarted() {
      return false;
    }

    @Override
    public void open() {}

    @Override
    public void close() {}

    @Override
    public void clear() {}

    @Override
    public void checkpoint(Mutation<?> mutation) {}
  }
}
