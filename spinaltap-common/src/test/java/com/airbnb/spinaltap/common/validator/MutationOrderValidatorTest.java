/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.validator;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.airbnb.spinaltap.Mutation;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MutationOrderValidatorTest {
  private final Mutation firstMutation = mock(Mutation.class);
  private final Mutation secondMutation = mock(Mutation.class);

  private final Mutation.Metadata firstMetadata = mock(Mutation.Metadata.class);
  private final Mutation.Metadata secondMetadata = mock(Mutation.Metadata.class);

  @Before
  public void setUp() throws Exception {
    when(firstMutation.getMetadata()).thenReturn(firstMetadata);
    when(secondMutation.getMetadata()).thenReturn(secondMetadata);
  }

  @Test
  public void testMutationInOrder() throws Exception {
    List<Mutation> unorderedMutations = Lists.newArrayList();

    when(firstMetadata.getId()).thenReturn(1L);
    when(secondMetadata.getId()).thenReturn(2L);

    MutationOrderValidator validator = new MutationOrderValidator(unorderedMutations::add);

    validator.validate(firstMutation);
    validator.validate(secondMutation);

    assertTrue(unorderedMutations.isEmpty());
  }

  @Test
  public void testMutationOutOfOrder() throws Exception {
    List<Mutation> unorderedMutations = Lists.newArrayList();

    when(firstMetadata.getId()).thenReturn(2L);
    when(secondMetadata.getId()).thenReturn(1L);

    MutationOrderValidator validator = new MutationOrderValidator(unorderedMutations::add);

    validator.validate(firstMutation);
    validator.validate(secondMutation);

    assertEquals(Arrays.asList(secondMutation), unorderedMutations);
  }

  @Test
  public void testReset() throws Exception {
    List<Mutation> unorderedMutations = Lists.newArrayList();

    when(firstMetadata.getId()).thenReturn(1L);
    when(secondMetadata.getId()).thenReturn(2L);

    MutationOrderValidator validator = new MutationOrderValidator(unorderedMutations::add);

    validator.validate(firstMutation);
    validator.validate(secondMutation);

    validator.reset();

    validator.validate(firstMutation);
    validator.validate(secondMutation);

    assertTrue(unorderedMutations.isEmpty());
  }
}
