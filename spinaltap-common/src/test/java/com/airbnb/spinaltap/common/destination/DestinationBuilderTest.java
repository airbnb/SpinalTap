/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.airbnb.common.metrics.TaggedMetricRegistry;
import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.KeyProvider;
import com.airbnb.spinaltap.common.util.Mapper;
import lombok.NoArgsConstructor;
import org.junit.Test;

public class DestinationBuilderTest {
  private static final Mapper<Mutation<?>, Mutation<?>> mapper = mutation -> mutation;
  private static final DestinationMetrics metrics =
      new DestinationMetrics("test", "test", new TaggedMetricRegistry());

  @Test(expected = NullPointerException.class)
  public void testNoMapper() throws Exception {
    new TestDestinationBuilder().withMetrics(metrics).build();
  }

  @Test(expected = NullPointerException.class)
  public void testNoMetrics() throws Exception {
    new TestDestinationBuilder().withMapper(mapper).build();
  }

  @Test
  public void testBuildBufferedDestination() throws Exception {
    Destination destination =
        new TestDestinationBuilder().withMapper(mapper).withMetrics(metrics).withBuffer(5).build();

    assertTrue(destination instanceof BufferedDestination);
    assertEquals(5, ((BufferedDestination) destination).getRemainingCapacity());
  }

  @Test
  public void testBuildDestinationPool() throws Exception {
    Destination destination =
        new TestDestinationBuilder()
            .withMapper(mapper)
            .withMetrics(metrics)
            .withBuffer(5)
            .withPool(7, mock(KeyProvider.class))
            .build();

    assertTrue(destination instanceof DestinationPool);
    assertEquals(7, ((DestinationPool) destination).getPoolSize());
  }

  @NoArgsConstructor
  class TestDestinationBuilder extends DestinationBuilder<Mutation<?>> {
    @Override
    protected Destination createDestination() {
      return mock(Destination.class);
    }
  }
}
