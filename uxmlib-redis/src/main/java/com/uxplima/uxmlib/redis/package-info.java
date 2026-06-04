/**
 * A low-level binary Redis pub/sub primitive. {@link com.uxplima.uxmlib.redis.RedisBus} publishes raw
 * {@code byte[]} frames to a named channel and subscribes to one, owning only the wire (Lettuce
 * PUBLISH / SUBSCRIBE) — encoding, routing and self-echo handling are left to the caller. Useful for any
 * plugin that fans a binary message out across the server nodes sharing one Redis, without taking on the
 * storage toolkit's relational dependencies.
 */
@NullMarked
package com.uxplima.uxmlib.redis;

import org.jspecify.annotations.NullMarked;
