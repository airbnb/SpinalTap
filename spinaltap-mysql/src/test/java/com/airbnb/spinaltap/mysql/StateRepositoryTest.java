/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class StateRepositoryTest {
  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);
  private final Repository<SourceState> repository = mock(Repository.class);

  private final StateRepository stateRepository = new StateRepository("test", repository, metrics);

  @Test
  public void testSave() throws Exception {
    SourceState state = mock(SourceState.class);
    SourceState nextState = mock(SourceState.class);
    AtomicReference<SourceState> updatedState = new AtomicReference<>();

    when(state.getCurrentLeaderEpoch()).thenReturn(5l);

    doAnswer(
            new Answer<SourceState>() {
              @Override
              public SourceState answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();

                SourceState newState = (SourceState) args[0];
                Repository.DataUpdater<SourceState> updater =
                    (Repository.DataUpdater<SourceState>) args[1];

                updatedState.set(updater.apply(state, newState));

                return null;
              }
            })
        .when(repository)
        .update(any(SourceState.class), any(Repository.DataUpdater.class));

    // Test new leader epoch leader less than current
    when(nextState.getCurrentLeaderEpoch()).thenReturn(4l);
    stateRepository.save(nextState);
    assertEquals(state, updatedState.get());

    // Test new leader epoch leader same as current
    when(nextState.getCurrentLeaderEpoch()).thenReturn(5l);
    stateRepository.save(nextState);
    assertEquals(nextState, updatedState.get());

    // Test new leader epoch leader greather current
    when(nextState.getCurrentLeaderEpoch()).thenReturn(6l);
    stateRepository.save(nextState);
    assertEquals(nextState, updatedState.get());
  }

  @Test(expected = RuntimeException.class)
  public void testSaveFailure() throws Exception {
    doThrow(new RuntimeException())
        .when(repository)
        .update(any(SourceState.class), any(Repository.DataUpdater.class));

    try {
      stateRepository.save(mock(SourceState.class));
    } catch (RuntimeException ex) {
      verify(metrics, times(1)).stateSaveFailure(ex);
      throw ex;
    }
  }

  @Test
  public void testRead() throws Exception {
    SourceState state = mock(SourceState.class);

    when(repository.get()).thenReturn(state);
    when(repository.exists()).thenReturn(false);

    assertNull(stateRepository.read());

    when(repository.exists()).thenReturn(true);

    Assert.assertEquals(state, stateRepository.read());
    verify(metrics, times(2)).stateRead();
  }

  @Test(expected = RuntimeException.class)
  public void testReadFailure() throws Exception {
    when(repository.exists()).thenThrow(new RuntimeException());

    try {
      stateRepository.read();
    } catch (RuntimeException ex) {
      verify(metrics, times(1)).stateReadFailure(ex);
      throw ex;
    }
  }
}
