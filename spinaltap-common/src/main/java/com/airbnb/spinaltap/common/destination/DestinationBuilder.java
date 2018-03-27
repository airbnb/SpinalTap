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
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

@NoArgsConstructor
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

  public DestinationBuilder<T> withMapper(BatchMapper<Mutation<?>, T> mapper) {
    this.mapper = mapper;
    return this;
  }

  public DestinationBuilder<T> withMapper(Mapper<Mutation<?>, T> mapper) {
    this.mapper = mutations -> mutations.stream().map(mapper::map).collect(Collectors.toList());

    return this;
  }

  public DestinationBuilder<T> withTopicNamePrefix(String topicNamePrefix) {
    this.topicNamePrefix = topicNamePrefix;
    return this;
  }

  public DestinationBuilder<T> withMetrics(DestinationMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public DestinationBuilder<T> withBuffer(int bufferSize) {
    this.bufferSize = bufferSize;
    return this;
  }

  public DestinationBuilder<T> withPool(
      int poolSize, KeyProvider<Mutation<?>, String> keyProvider) {
    this.poolSize = poolSize;
    this.keyProvider = keyProvider;
    return this;
  }

  public DestinationBuilder<T> withValidation() {
    this.validationEnabled = true;
    return this;
  }

  public DestinationBuilder<T> withLargeMessage(boolean largeMessageEnabled) {
    this.largeMessageEnabled = largeMessageEnabled;
    return this;
  }

  public DestinationBuilder<T> withDelaySendMs(long delaySendMs) {
    this.delaySendMs = delaySendMs;
    return this;
  }

  public Destination build() {
    Preconditions.checkNotNull(mapper, "Mapper was not specified");
    Preconditions.checkNotNull(metrics, "Metrics were not specified");

    Supplier<Destination> supplier =
        () -> {
          Destination destination = createDestination();

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

  private Destination createDestinationPool(Supplier<Destination> supplier) {
    Preconditions.checkNotNull(keyProvider, "Key provider was not specified");

    List<Destination> destinations = Lists.newArrayList();
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
