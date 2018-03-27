/**
 * Copyright 2018 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.util;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.curator.framework.CuratorFramework;

/**
 * {@link Repository} implement where objects are stored in Zookeeper.
 *
 * @param <T> the object type.
 */
@RequiredArgsConstructor
public class ZookeeperRepository<T> implements Repository<T> {
  @NonNull private final CuratorFramework zkClient;
  @NonNull private final String path;
  @NonNull private final TypeReference<? extends T> propertyClass;

  @Override
  public boolean exists() throws Exception {
    return zkClient.checkExists().forPath(path) != null;
  }

  @Override
  public void create(T data) throws Exception {
    zkClient
        .create()
        .creatingParentsIfNeeded()
        .forPath(path, JsonUtil.OBJECT_MAPPER.writeValueAsBytes(data));
  }

  @Override
  public void set(T data) throws Exception {
    zkClient.setData().forPath(path, JsonUtil.OBJECT_MAPPER.writeValueAsBytes(data));
  }

  @Override
  public void update(T data, DataUpdater<T> updater) throws Exception {
    if (exists()) {
      set(updater.apply(get(), data));
    } else {
      create(data);
    }
  }

  @Override
  public T get() throws Exception {
    byte[] value = zkClient.getData().forPath(path);
    return JsonUtil.OBJECT_MAPPER.readValue(value, propertyClass);
  }
}
