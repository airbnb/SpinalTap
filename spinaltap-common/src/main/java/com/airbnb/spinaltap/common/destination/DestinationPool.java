/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.destination;

import com.airbnb.spinaltap.Mutation;
import com.airbnb.spinaltap.common.util.KeyProvider;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DestinationPool extends ListenableDestination {
  private final KeyProvider<Mutation<?>, String> keyProvider;
  private final List<Destination> destinations;
  private final boolean[] isActive;

  private Listener destinationListener =
      new Listener() {
        public void onError(Exception ex) {
          notifyError(ex);
        }
      };

  public DestinationPool(
      KeyProvider<Mutation<?>, String> keyProvider, List<Destination> destinations) {
    this.destinations = destinations;
    this.keyProvider = keyProvider;

    this.isActive = new boolean[destinations.size()];

    destinations.forEach(destination -> destination.addListener(destinationListener));
  }

  public int getPoolSize() {
    return destinations.size();
  }

  /**
   * Gets the mutation with the earliest id from the last published mutations of the destinations in
   * the pool
   *
   * <p>Note: If there is any destination that we have sent mutations to but has not published yet,
   * it will return null as its lastPublishedMutation. This might lead to data loss if 1) that
   * destination fails to publish, and 2) we checkpoint on another destination's last mutation that
   * is ahead in position.
   *
   * <p>The solution is to avoid checkpointing in this scenario by returning null. isActive is used
   * in order to disregard destinations that have not yet been sent any mutations
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
   * Partitions the mutation list according to the supplied key provider and routes to the
   * corresponding destinations
   */
  @Override
  public synchronized void send(List<? extends Mutation<?>> mutations) {
    partition(mutations)
        .forEach(
            (id, mutationList) -> {
              log.debug("Sending {} mutations to destination {}", mutationList.size(), id);

              // Always retain this order
              isActive[id] = true;
              destinations.get(id).send(mutationList);
            });
  }

  private Map<Integer, List<Mutation<?>>> partition(List<? extends Mutation<?>> mutations) {
    Map<Integer, List<Mutation<?>>> partitions = Maps.newLinkedHashMap();

    mutations.forEach(
        mutation -> {
          int partitionId = getPartitionId(mutation);

          partitions.putIfAbsent(partitionId, Lists.newArrayList());
          partitions.get(partitionId).add(mutation);
        });

    return partitions;
  }

  /** The is currently a replication of the logic in {@link kafka.producer.DefaultPartitioner} */
  private int getPartitionId(Mutation<?> mutation) {
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
