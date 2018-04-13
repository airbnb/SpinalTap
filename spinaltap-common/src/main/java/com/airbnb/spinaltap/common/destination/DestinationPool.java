/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import static java.util.stream.Collectors.groupingBy;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.KeyProvider;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a pool of {@link Destination}s, where events are routed to the appropriate {@link
 * Destination} given a partitioning function based on {@link Mutation} key.
 *
 * <p>Note: This implement helps to fan-out load, which is particularly useful to keep {@link
 * Mutation} lag low when there are event spikes.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DestinationPool extends ListenableDestination {
  @NonNull private final KeyProvider<Mutation<?>, String> keyProvider;
  @NonNull private final List<Destination> destinations;
  @NonNull private final boolean[] isActive;

  private Listener destinationListener =
      new Listener() {
        public void onError(Exception ex) {
          notifyError(ex);
        }
      };

  public DestinationPool(
      @NonNull final KeyProvider<Mutation<?>, String> keyProvider,
      @NonNull final List<Destination> destinations) {
    this(keyProvider, destinations, new boolean[destinations.size()]);

    destinations.forEach(destination -> destination.addListener(destinationListener));
  }

  public int getPoolSize() {
    return destinations.size();
  }

  /**
   * Gets the {@link Mutation} with the earliest id from the last published {@link Mutation}s across
   * all {@link Destination}s in the pool.
   *
   * <p>Note: If there is any {@link Destination} that we have sent {@link Mutation}s to but has not
   * been published yet, null will be returned as the last published {@link Mutation}. This might
   * lead to data loss if 1) the {@code Destination} fails to publish, and 2) we checkpoint on
   * another {@link Destination}'s last published {@link Mutation} that is ahead in position. The
   * solution is to avoid checkpointing in this scenario by returning null. {@code isActive} is used
   * in order to disregard {@link Destination}s that have not yet been sent any {@link Mutation}s.
   */
  @Override
  public synchronized Mutation<?> getLastPublishedMutation() {
    for (int i = 0; i < destinations.size(); i++) {
      if (isActive[i] && destinations.get(i).getLastPublishedMutation() == null) {
        return null;
      }
    }

    return destinations
        .stream()
        .map(Destination::getLastPublishedMutation)
        .filter(Objects::nonNull)
        .min(Comparator.comparingLong(mutation -> mutation.getMetadata().getId()))
        .orElse(null);
  }

  /**
   * Partitions the {@link Mutation} list according to the supplied {@link KeyProvider} and routes
   * to the corresponding {@link Destination}s
   */
  @Override
  public synchronized void send(@NonNull final List<? extends Mutation<?>> mutations) {
    mutations
        .stream()
        .collect(groupingBy(this::getPartitionId))
        .forEach(
            (id, mutationList) -> {
              log.debug("Sending {} mutations to destination {}.", mutationList.size(), id);

              // Always retain this order
              isActive[id] = true;
              destinations.get(id).send(mutationList);
            });
  }

  /** The is currently a replication of the logic in {@link kafka.producer.DefaultPartitioner} */
  private int getPartitionId(final Mutation<?> mutation) {
    return Math.abs(keyProvider.get(mutation).hashCode() % destinations.size());
  }

  @Override
  public boolean isStarted() {
    return destinations.stream().allMatch(Destination::isStarted);
  }

  @Override
  public void open() {
    Arrays.fill(isActive, false);
    destinations.parallelStream().forEach(Destination::open);
  }

  @Override
  public void close() {
    destinations.parallelStream().forEach(Destination::close);
  }

  @Override
  public void clear() {
    destinations.parallelStream().forEach(Destination::clear);
  }
}
