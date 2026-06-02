/**
 * Cross-server fan-out: {@link com.uxplima.uxmlib.storage.sync.DataSynchronizer} is a publish/subscribe SPI
 * used for cache invalidation and small events between server nodes.
 * {@link com.uxplima.uxmlib.storage.sync.LocalDataSynchronizer} is the in-memory single-node default;
 * {@link com.uxplima.uxmlib.storage.sync.RedisDataSynchronizer} bridges it across nodes over Redis pub/sub
 * (constructed only when Redis is configured). {@link com.uxplima.uxmlib.storage.sync.SyncMessage} is the
 * pure wire codec that lets one physical Redis channel carry many logical ones.
 */
@NullMarked
package com.uxplima.uxmlib.storage.sync;

import org.jspecify.annotations.NullMarked;
