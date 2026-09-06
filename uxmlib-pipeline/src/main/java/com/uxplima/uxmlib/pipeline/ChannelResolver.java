package com.uxplima.uxmlib.pipeline;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;

/**
 * The one class that holds the unavoidable NMS reflection. It reaches a player's raw Netty
 * {@link Channel} via {@code CraftPlayer.getHandle() -> connection -> channel}, walking by field
 * <em>type</em> rather than obfuscated field <em>name</em> so it survives Mojang's remapping across 1.21.x.
 * Every step is guarded; the whole thing returns an {@link Optional} and <b>never throws</b>: on any mock
 * player, security manager, or layout change it simply returns {@link Optional#empty()}.
 *
 * <p>A {@code ServerPlayer} object graph can expose more than one {@link Channel}-typed reference (an
 * embedded/local channel for integrated-server play, a wrapped child channel), so the walk does not blindly
 * take the first Channel it sees: it prefers a channel whose pipeline actually carries the gameplay
 * {@code anchor} handler (the vanilla {@code "decoder"}), and only falls back to the first open channel when
 * no anchor-bearing one is found. That keeps the interceptor from anchoring onto a channel that never sees
 * gameplay packets.
 *
 * <p>Why so much reflection: Paper exposes no public handle to the connection's Netty channel, and a
 * from-scratch (MIT) packet layer cannot use PacketEvents (GPL) to get it. Isolating it in this one class
 * keeps the rest of the module free of NMS. Every {@code setAccessible}/read is guarded and fails closed.
 */
public final class ChannelResolver {

    /** How deep into nested connection objects we search for a Channel field before giving up. */
    private static final int MAX_DEPTH = 4;

    private final String anchorName;

    /** Resolve preferring a channel whose pipeline carries the default {@code "decoder"} anchor. */
    public ChannelResolver() {
        this(PacketPipeline.DEFAULT_ANCHOR);
    }

    /**
     * @param anchorName the pipeline handler a gameplay channel must contain to be preferred over any other
     *     open channel reachable in the object graph (e.g. {@code "decoder"})
     */
    public ChannelResolver(String anchorName) {
        this.anchorName = Objects.requireNonNull(anchorName, "anchorName");
    }

    /**
     * Resolve the Netty channel backing {@code player}'s connection.
     *
     * @return the channel, or empty if the player is not a CraftPlayer / the layout could not be walked / the
     *     channel is not open
     */
    public Optional<Channel> resolve(Player player) {
        Objects.requireNonNull(player, "player");
        @Nullable Object handle = invokeNoArg(player, "getHandle");
        if (handle == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(findChannel(handle));
    }

    /**
     * Breadth-first search for an open {@link Channel} reachable from {@code root}. A channel whose pipeline
     * holds the {@code anchor} handler is returned the moment it is found; any other open channel is kept only
     * as a fallback and returned if the walk finds no anchor-bearing channel.
     *
     * <p>Package-private so the object-graph walk can be exercised directly with a synthetic handle, since
     * MockBukkit cannot give us a {@code CraftPlayer.getHandle()} chain to walk.
     */
    @Nullable Channel findChannel(Object root) {
        Deque<Node> queue = new ArrayDeque<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(new Node(root, MAX_DEPTH));
        seen.add(root);
        @Nullable Channel fallback = null;
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            for (Field field : declaredFields(node.value.getClass())) {
                @Nullable Object value = unwrap(read(field, node.value));
                if (value instanceof Channel channel) {
                    if (channel.isOpen() && hasAnchor(channel)) {
                        return channel;
                    }
                    if (fallback == null && channel.isOpen()) {
                        fallback = channel;
                    }
                } else if (value != null && node.depth > 0 && shouldDescend(field) && seen.add(value)) {
                    queue.add(new Node(value, node.depth - 1));
                }
            }
        }
        return fallback;
    }

    private boolean hasAnchor(Channel channel) {
        try {
            return channel.pipeline().get(anchorName) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Descend into reference fields, but never into primitives or JDK leaf types (String, boxes, time/math). */
    private static boolean shouldDescend(Field field) {
        Class<?> type = field.getType();
        if (type.isPrimitive() || type.isEnum()) {
            return false;
        }
        String name = type.getName();
        return !name.startsWith("java.lang.")
                && !name.startsWith("java.time.")
                && !name.startsWith("java.math.")
                && !name.startsWith("java.io.")
                && !name.startsWith("java.net.")
                && !name.startsWith("java.nio.")
                && !name.startsWith("[");
    }

    /** Peel a single layer of {@link AtomicReference}/{@link Optional}/{@link Collection} that may hold the channel. */
    private static @Nullable Object unwrap(@Nullable Object value) {
        if (value instanceof AtomicReference<?> ref) {
            return ref.get();
        }
        if (value instanceof Optional<?> opt) {
            return opt.orElse(null);
        }
        if (value instanceof Collection<?> collection) {
            try {
                for (Object element : collection) {
                    if (element instanceof Channel) {
                        return element;
                    }
                }
            } catch (RuntimeException uniterable) {
                // Some game-state collections reachable in the object graph (e.g. a specialised redstone queue
                // whose iterator() throws UnsupportedOperationException) cannot be walked. The resolver's
                // contract is to fail closed, so treat such a field as carrying no channel and keep walking the
                // rest of the graph rather than letting the exception escape and abort every packet send.
                return null;
            }
        }
        return value;
    }

    /**
     * Every field declared on {@code type} <em>and its superclasses</em>. Walking the hierarchy is essential:
     * since 1.20.2 the {@code Connection} that holds the channel is reached through the {@code connection} field
     * declared on {@code ServerCommonPacketListenerImpl}, a superclass of the game packet listener, so a
     * declared-fields-only walk stops at the listener and never reaches the channel.
     */
    private static Field[] declaredFields(Class<?> type) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Collections.addAll(fields, c.getDeclaredFields());
            } catch (SecurityException ignored) {
                // Skip an unreadable level and keep walking up; a missing level just narrows the search.
            }
        }
        return fields.toArray(new Field[0]);
    }

    private static @Nullable Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // RuntimeException covers SecurityException/InaccessibleObjectException; on any of them we fail
            // closed and the channel simply resolves to empty.
            return null;
        }
    }

    private static @Nullable Object invokeNoArg(Object target, String method) {
        @Nullable Method found = findNoArgMethod(target.getClass(), method);
        if (found == null) {
            return null;
        }
        try {
            found.setAccessible(true);
            return found.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            // RuntimeException covers SecurityException/InaccessibleObjectException; fail closed to empty.
            return null;
        }
    }

    /** Find a no-arg method by name, walking the class hierarchy so a non-public {@code getHandle} still resolves. */
    private static @Nullable Method findNoArgMethod(Class<?> type, String method) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(method);
            } catch (NoSuchMethodException | SecurityException ignored) {
                // Try the superclass; a public getHandle() is found on the first hop, a relocated one later.
            }
        }
        return null;
    }

    /** A graph node paired with the remaining descent budget. */
    private record Node(Object value, int depth) {}
}
