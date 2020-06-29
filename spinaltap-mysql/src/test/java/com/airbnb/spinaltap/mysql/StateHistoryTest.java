/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.airbnb.spinaltap.common.source.MysqlSourceState;
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
    MysqlSourceState firstState = mock(MysqlSourceState.class);
    MysqlSourceState secondState = mock(MysqlSourceState.class);
    MysqlSourceState thirdState = mock(MysqlSourceState.class);
    MysqlSourceState fourthState = mock(MysqlSourceState.class);

    TestRepository repository = new TestRepository(firstState);
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 2, repository, metrics);

    history.add(secondState);

    assertEquals(Arrays.asList(firstState, secondState), repository.get());

    history.add(thirdState);
    history.add(fourthState);

    assertEquals(Arrays.asList(thirdState, fourthState), repository.get());
  }

  @Test
  public void testEmptyHistory() throws Exception {
    MysqlSourceState state = mock(MysqlSourceState.class);

    TestRepository repository = new TestRepository();
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 2, repository, metrics);
    assertTrue(history.isEmpty());

    repository = new TestRepository(state);
    history = new StateHistory<>(SOURCE_NAME, 2, repository, metrics);
    assertFalse(history.isEmpty());
  }

  @Test
  public void testRemoveLastFromHistory() throws Exception {
    MysqlSourceState firstState = mock(MysqlSourceState.class);
    MysqlSourceState secondState = mock(MysqlSourceState.class);
    MysqlSourceState thirdState = mock(MysqlSourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState, thirdState);
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 3, repository, metrics);

    assertEquals(thirdState, history.removeLast());
    assertEquals(secondState, history.removeLast());
    assertEquals(firstState, history.removeLast());
    assertTrue(history.isEmpty());
  }

  @Test(expected = IllegalStateException.class)
  public void testRemoveFromEmptyHistory() throws Exception {
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 2, new TestRepository(), metrics);
    history.removeLast();
  }

  @Test(expected = IllegalStateException.class)
  public void testRemoveMoreElementsThanInHistory() throws Exception {
    MysqlSourceState firstState = mock(MysqlSourceState.class);
    MysqlSourceState secondState = mock(MysqlSourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState);
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 2, repository, metrics);

    history.removeLast(3);
  }

  @Test
  public void testRemoveAllElementsFromHistory() throws Exception {
    MysqlSourceState firstState = mock(MysqlSourceState.class);
    MysqlSourceState secondState = mock(MysqlSourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState);
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 2, repository, metrics);

    assertEquals(firstState, history.removeLast(2));
    assertTrue(history.isEmpty());
  }

  @Test
  public void testRemoveMultipleElementsFromHistory() throws Exception {
    MysqlSourceState firstState = mock(MysqlSourceState.class);
    MysqlSourceState secondState = mock(MysqlSourceState.class);
    MysqlSourceState thirdState = mock(MysqlSourceState.class);

    TestRepository repository = new TestRepository(firstState, secondState, thirdState);
    StateHistory<MysqlSourceState> history =
        new StateHistory<>(SOURCE_NAME, 3, repository, metrics);

    assertEquals(secondState, history.removeLast(2));
    assertEquals(Collections.singletonList(firstState), repository.get());
  }

  @Test
  public void testRemoveStateHistory() throws Exception {
    TestRepository repository = new TestRepository(mock(MysqlSourceState.class));
    assertTrue(repository.exists());
    repository.remove();
    assertFalse(repository.exists());
  }

  @NoArgsConstructor
  @AllArgsConstructor
  public static class TestRepository implements Repository<Collection<MysqlSourceState>> {
    private List<MysqlSourceState> states;

    TestRepository(MysqlSourceState... states) {
      this(Arrays.asList(states));
    }

    @Override
    public boolean exists() throws Exception {
      return states != null;
    }

    @Override
    public void create(Collection<MysqlSourceState> states) throws Exception {
      this.states = Lists.newArrayList(states);
    }

    @Override
    public void set(Collection<MysqlSourceState> states) throws Exception {
      create(states);
    }

    @Override
    public void update(
        Collection<MysqlSourceState> states, DataUpdater<Collection<MysqlSourceState>> updater)
        throws Exception {
      create(states);
    }

    @Override
    public Collection<MysqlSourceState> get() throws Exception {
      return states;
    }

    @Override
    public void remove() throws Exception {
      states = null;
    }
  }
}
