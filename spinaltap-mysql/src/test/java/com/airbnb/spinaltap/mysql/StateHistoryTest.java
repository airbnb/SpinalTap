/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.airbnb.spinaltap.common.source.SourceState;
import com.airbnb.spinaltap.common.util.Repository;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.junit.Test;

public class StateHistoryTest {
  private final String SOURCE_NAME = "test_source";
  private final MysqlSourceMetrics metrics = mock(MysqlSourceMetrics.class);

  @Test
  public void test() throws Exception {
    SourceState firstState = mock(SourceState.class);
    SourceState secondState = mock(SourceState.class);
    SourceState thirdState = mock(SourceState.class);
    SourceState fourthState = mock(SourceState.class);

    TestRepository repository = new TestRepository(firstState);
    StateHistory history = new StateHistory(SOURCE_NAME, 2, repository, metrics);

    history.add(secondState);

    assertEquals(Arrays.asList(firstState, secondState), repository.get());

    history.add(thirdState);
    history.add(fourthState);

    assertEquals(Arrays.asList(thirdState, fourthState), repository.get());
  }

  @Test
  public void testEmptyHistory() throws Exception {
    SourceState state = mock(SourceState.class);

    TestRepository repository = new TestRepository();
    StateHistory history = new StateHistory(SOURCE_NAME, 2, repository, metrics);
    assertTrue(history.isEmpty());

    repository = new TestRepository(state);
    history = new StateHistory(SOURCE_NAME, 2, repository, metrics);
    assertFalse(history.isEmpty());
  }

  @Test
  public void testRemoveLastFromHistory() throws Exception {
    SourceState firstState = mock(SourceState.class);
    SourceState secondState = mock(SourceState.class);
    SourceState thirdState = mock(SourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState, thirdState);
    StateHistory history = new StateHistory(SOURCE_NAME, 3, repository, metrics);

    assertEquals(thirdState, history.removeLast());
    assertEquals(secondState, history.removeLast());
    assertEquals(firstState, history.removeLast());
    assertTrue(history.isEmpty());
  }

  @Test(expected = IllegalStateException.class)
  public void testRemoveFromEmptyHistory() throws Exception {
    StateHistory history = new StateHistory(SOURCE_NAME, 2, new TestRepository(), metrics);
    history.removeLast();
  }

  @Test(expected = IllegalStateException.class)
  public void testRemoveMoreElementsThanInHistory() throws Exception {
    SourceState firstState = mock(SourceState.class);
    SourceState secondState = mock(SourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState);
    StateHistory history = new StateHistory(SOURCE_NAME, 2, repository, metrics);

    history.removeLast(3);
  }

  @Test
  public void testRemoveAllElementsFromHistory() throws Exception {
    SourceState firstState = mock(SourceState.class);
    SourceState secondState = mock(SourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState);
    StateHistory history = new StateHistory(SOURCE_NAME, 2, repository, metrics);

    assertEquals(firstState, history.removeLast(2));
    assertTrue(history.isEmpty());
  }

  @Test
  public void testRemoveMultipleElementsFromHistory() throws Exception {
    SourceState firstState = mock(SourceState.class);
    SourceState secondState = mock(SourceState.class);
    SourceState thirdState = mock(SourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState, thirdState);
    StateHistory history = new StateHistory(SOURCE_NAME, 3, repository, metrics);

    assertEquals(secondState, history.removeLast(2));
    assertEquals(Collections.singletonList(firstState), repository.get());
  }

  @NoArgsConstructor
  @AllArgsConstructor
  public class TestRepository implements Repository<Collection<SourceState>> {
    private List<SourceState> states;

    TestRepository(SourceState... states) {
      this(Arrays.asList(states));
    }

    @Override
    public boolean exists() throws Exception {
      return states != null;
    }

    @Override
    public void create(Collection<SourceState> states) throws Exception {
      this.states = Lists.newArrayList(states);
    }

    @Override
    public void set(Collection<SourceState> states) throws Exception {
      create(states);
    }

    @Override
    public void update(Collection<SourceState> states, DataUpdater<Collection<SourceState>> updater)
        throws Exception {
      create(states);
    }

    @Override
    public Collection<SourceState> get() throws Exception {
      return states;
    }
  }
}
