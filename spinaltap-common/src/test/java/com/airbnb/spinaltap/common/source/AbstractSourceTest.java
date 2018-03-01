/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.source;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.exception.SourceException;
import com.airbnb.spinaltap.common.util.Filter;
import com.airbnb.spinaltap.common.util.Mapper;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Test;

public class AbstractSourceTest {
  private final SourceMetrics metrics = mock(SourceMetrics.class);
  private final SourceEvent event = mock(SourceEvent.class);
  private final Mapper<SourceEvent, List<? extends Mutation<?>>> mapper = mock(Mapper.class);
  private final Filter<SourceEvent> filter = mock(Filter.class);
  private final Source.Listener listener = mock(Source.Listener.class);

  private TestSource source;

  @Before
  public void setUp() throws Exception {
    source = new TestSource(metrics);
    source.addListener(listener);
  }

  @Test
  public void testOpenClose() throws Exception {
    source.open();

    assertTrue(source.isStarted());
    verify(metrics, times(1)).start();

    source.open();

    assertTrue(source.isStarted());
    verify(metrics, times(1)).start();

    source.close();

    assertFalse(source.isStarted());
    verify(metrics, times(1)).stop();

    source.close();

    assertFalse(source.isStarted());
    verify(metrics, times(2)).stop();
  }

  @Test(expected = SourceException.class)
  public void testOpenFailure() throws Exception {
    source.setStarted(false);
    source.setFailStart(true);

    try {
      source.open();
    } catch (RuntimeException ex) {
      verify(metrics, times(1)).startFailure(any(RuntimeException.class));
      verify(metrics, times(1)).stop();

      throw ex;
    }
  }

  @Test
  public void testProcessEvent() throws Exception {
    List mutations = Collections.singletonList(mock(Mutation.class));

    when(mapper.map(event)).thenReturn(mutations);
    when(filter.apply(event)).thenReturn(false);

    source.processEvent(event);

    verifyZeroInteractions(metrics);
    verify(listener, times(0)).onMutation(mutations);

    when(filter.apply(event)).thenReturn(true);

    source.processEvent(event);

    verify(listener, times(1)).onMutation(mutations);

    when(mapper.map(event)).thenReturn(Collections.emptyList());

    source.processEvent(event);

    verify(listener, times(2)).onEvent(event);
    verify(metrics, times(2)).eventReceived(event);
    verify(listener, times(1)).onMutation(mutations);
  }

  @Test(expected = SourceException.class)
  public void testProcessEventFailure() throws Exception {
    RuntimeException testException = new RuntimeException();

    when(filter.apply(event)).thenThrow(testException);

    source.setStarted(false);
    source.processEvent(event);

    verify(listener, times(0)).onMutation(any());
    verify(metrics, times(0)).eventFailure(testException);
    verify(listener, times(0)).onError(testException);

    source.setStarted(true);
    source.processEvent(event);

    verify(listener, times(0)).onMutation(any());
    verify(metrics, times(1)).eventFailure(testException);
    verify(listener, times(1)).onError(testException);
  }

  @Test(expected = SourceException.class)
  public void testOpenWhilePreviousProcessorRunning() throws Exception {
    source.open();
    source.setStarted(false);
    source.open();
  }

  @Getter
  @Setter
  class TestSource extends AbstractSource<SourceEvent> {
    private boolean started = false;
    private boolean terminated = true;

    private boolean failStart;
    private boolean failStop;

    public TestSource(SourceMetrics metrics) {
      super("test", metrics, mapper, filter);
    }

    public void commitCheckpoint(Mutation metadata) {}

    @Override
    public boolean isStarted() {
      return isRunning();
    }

    @Override
    protected boolean isRunning() {
      return started;
    }

    @Override
    protected boolean isTerminated() {
      return terminated;
    }

    @Override
    public void start() {
      if (failStart) {
        throw new RuntimeException();
      }

      started = true;
      terminated = false;
    }

    @Override
    public void stop() {
      if (failStop) {
        throw new RuntimeException();
      }

      started = false;
      terminated = true;
    }

    @Override
    protected void initialize() {}
  }
}
