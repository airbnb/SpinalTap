/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.mysql;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.common.source.SourceEvent;
import com.airbnb.spinaltap.mysql.event.BinlogEvent;
import com.airbnb.spinaltap.mysql.validator.EventOrderValidator;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class EventOrderValidatorTest {
  private final BinlogEvent firstEvent = mock(BinlogEvent.class);
  private final BinlogEvent secondEvent = mock(BinlogEvent.class);

  @Test
  public void testEventInOrder() throws Exception {
    List<SourceEvent> unorderedEvents = Lists.newArrayList();

    when(firstEvent.getOffset()).thenReturn(1L);
    when(secondEvent.getOffset()).thenReturn(2L);

    EventOrderValidator validator = new EventOrderValidator(unorderedEvents::add);

    validator.validate(firstEvent);
    validator.validate(secondEvent);

    assertTrue(unorderedEvents.isEmpty());
  }

  @Test
  public void testEventOutOfOrder() throws Exception {
    List<SourceEvent> unorderedEvents = Lists.newArrayList();

    when(firstEvent.getOffset()).thenReturn(2L);
    when(secondEvent.getOffset()).thenReturn(1L);

    EventOrderValidator validator = new EventOrderValidator(unorderedEvents::add);

    validator.validate(firstEvent);
    validator.validate(secondEvent);

    assertEquals(Collections.singletonList(secondEvent), unorderedEvents);
  }

  @Test
  public void testReset() throws Exception {
    List<SourceEvent> unorderedEvents = Lists.newArrayList();

    when(firstEvent.getOffset()).thenReturn(1L);
    when(secondEvent.getOffset()).thenReturn(2L);

    EventOrderValidator validator = new EventOrderValidator(unorderedEvents::add);

    validator.validate(firstEvent);
    validator.validate(secondEvent);

    validator.reset();

    validator.validate(firstEvent);
    validator.validate(secondEvent);

    assertTrue(unorderedEvents.isEmpty());
  }
}
