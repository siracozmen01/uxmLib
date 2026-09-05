/**
 * <b>EXPERIMENTAL: the Netty pipeline, and nothing above it.</b> This module holds the channel plumbing that
 * every packet feature in this library stands on: resolve a player's channel, inject and eject a handler,
 * keep it in place, and offer a seam a listener can sit in. It builds no packet and knows no entity. The
 * features that do, the fake NPCs, the per-viewer tab list and nametags, live in {@code uxmlib-packet} and
 * {@code uxmlib-nametags} and depend on this.
 *
 * <p>It is kept separate for one reason worth stating: it needs no Mojang-mapped server. Everything above it
 * is compiled against the dev bundle and published Mojang mapped, which asks a consumer to ship the
 * namespace manifest attribute. This module asks for nothing but Paper and Netty, so a plugin that wants a
 * packet pipeline and no server internals can take it alone.
 *
 * <p>PacketEvents, the obvious off-the-shelf choice, is GPL, so none of it is borrowed; the technique here is
 * modelled on the MIT HamsterAPI pipeline-injection blueprint and written from scratch.
 *
 * <p>What is real and usable today:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.pipeline.PacketListener}: the seam: {@code onSend}/{@code onReceive} return a
 *       {@link com.uxplima.uxmlib.pipeline.PacketAction} (pass or cancel). A listener may instead override
 *       {@code onSendVerdict} to return a {@link com.uxplima.uxmlib.pipeline.PacketVerdict}, which also supports a
 *       {@code rewrite} that forwards a replacement packet downstream on the outbound path. Listeners never
 *       throw across the channel; faults are swallowed and the packet passes (fail-open).
 *   <li>{@link com.uxplima.uxmlib.pipeline.PacketListenerRegistry}: an ordered, thread-safe registry that
 *       dispatches a packet to every listener and folds their decisions into a single pass/cancel/rewrite
 *       verdict (cancel vetoes; the first rewrite otherwise wins). Pure logic; fully unit-tested.
 *   <li>{@link com.uxplima.uxmlib.pipeline.PipelineWatchdog}: the self-healing reorder decision: given the live
 *       handler names, our handler name and its anchor, it decides whether our handler still sits directly
 *       after the anchor and, if not, what move restores it. Pure logic; fully unit-tested.
 *   <li>{@link com.uxplima.uxmlib.pipeline.ChannelResolver}: the single class that holds the unavoidable NMS
 *       reflection: {@code CraftPlayer.getHandle() -> connection -> channel}, reached by field <em>type</em>
 *       not obfuscated name, every step guarded. Returns an {@link java.util.Optional}; it never throws.
 *   <li>{@link com.uxplima.uxmlib.pipeline.PacketPipeline}: the injector: inject/eject a named
 *       {@link io.netty.channel.ChannelDuplexHandler} into a player's connection channel, idempotently, with
 *       a {@link com.uxplima.uxmlib.pipeline.PipelineWatchdog}-driven reorder pass.
 * </ul>
 *
 * <p>What is <em>not</em> here (and why a server cannot yet show an NPC with this module alone): packet
 * <em>encoding</em> (we forward raw netty messages but do not construct spawn/metadata/equipment packets),
 * any entity-id allocator, per-viewer interest tracking, or skin resolution. Those are the next milestone and
 * will reuse the holograms' two-set per-viewer lifecycle. Treat every type here as unstable API.
 *
 * <p>MockBukkit cannot provide a real Netty channel, so {@link com.uxplima.uxmlib.pipeline.ChannelResolver} and
 * {@link com.uxplima.uxmlib.pipeline.PacketPipeline} are smoke-tested only: the contract under test is that they
 * fail gracefully (return empty / report not-injected) against a mock player rather than throwing.
 */
@NullMarked
package com.uxplima.uxmlib.pipeline;

import org.jspecify.annotations.NullMarked;
