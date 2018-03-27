/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.BatchMapper;
import com.airbnb.spinaltap.common.util.KeyProvider;
import com.airbnb.spinaltap.common.util.Mapper;
import com.airbnb.spinaltap.common.util.Validator;
import com.airbnb.spinaltap.common.validator.MutationOrderValidator;

import lombok.NonNull;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.validation.constraints.Min;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public abstract class DestinationBuilder<T> {
  protected BatchMapper<Mutation<?>, T> mapper;
  protected DestinationMetrics metrics;
  protected String topicNamePrefix = "spinaltap";
  protected boolean largeMessageEnabled = false;
  protected long delaySendMs = 0;

  private KeyProvider<Mutation<?>, String> keyProvider;
  private int bufferSize = 0;
  private int poolSize = 0;
  private boolean validationEnabled = false;

  public DestinationBuilder<T> withMapper(@NonNull final BatchMapper<Mutation<?>, T> mapper) {
    this.mapper = mapper;
    return this;
  }

  public final DestinationBuilder<T> withMapper(@NonNull final Mapper<Mutation<?>, T> mapper) {
    this.mapper = mutations -> mutations.stream().map(mapper::map).collect(Collectors.toList());

    return this;
  }

  public final DestinationBuilder<T> withTopicNamePrefix(@NonNull final String topicNamePrefix) {
    this.topicNamePrefix = topicNamePrefix;
    return this;
  }

  public final DestinationBuilder<T> withMetrics(@NonNull final DestinationMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public final DestinationBuilder<T> withBuffer(@Min(0) final int bufferSize) {
    this.bufferSize = bufferSize;
    return this;
  }

  public final DestinationBuilder<T> withPool(
      @Min(0) final int poolSize, @NonNull final KeyProvider<Mutation<?>, String> keyProvider) {
    this.poolSize = poolSize;
    this.keyProvider = keyProvider;
    return this;
  }

  public final DestinationBuilder<T> withValidation() {
    this.validationEnabled = true;
    return this;
  }

  public final DestinationBuilder<T> withLargeMessage(final boolean largeMessageEnabled) {
    this.largeMessageEnabled = largeMessageEnabled;
    return this;
  }

  public DestinationBuilder<T> withDelaySendMs(long delaySendMs) {
    this.delaySendMs = delaySendMs;
    return this;
  }

  public final Destination build() {
    Preconditions.checkNotNull(mapper, "Mapper was not specified.");
    Preconditions.checkNotNull(metrics, "Metrics were not specified.");

    final Supplier<Destination> supplier =
        () -> {
          final Destination destination = createDestination();

          if (validationEnabled) {
            registerValidator(destination, new MutationOrderValidator(metrics::outOfOrder));
          }

          if (bufferSize > 0) {
            return new BufferedDestination(bufferSize, destination, metrics);
          }

          return destination;
        };

    if (poolSize > 0) {
      return createDestinationPool(supplier);
    }

    return supplier.get();
  }

  protected abstract Destination createDestination();

  private Destination createDestinationPool(final Supplier<Destination> supplier) {
    Preconditions.checkNotNull(keyProvider, "Key provider was not specified");

    final List<Destination> destinations = Lists.newArrayList();
    for (int i = 0; i < poolSize; i++) {
      destinations.add(supplier.get());
    }

    return new DestinationPool(keyProvider, destinations);
  }

  private void registerValidator(Destination destination, Validator<Mutation<?>> validator) {
    destination.addListener(
        new Destination.Listener() {
          @Override
          public void onStart() {
            validator.reset();
          }

          @Override
          public void onSend(List<? extends Mutation<?>> mutations) {
            mutations.forEach(validator::validate);
          }
        });
  }
}
