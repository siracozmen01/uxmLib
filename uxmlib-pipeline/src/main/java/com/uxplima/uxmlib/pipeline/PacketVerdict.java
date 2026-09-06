package com.uxplima.uxmlib.pipeline;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A listener's verdict on a packet, the richer form that can also <em>rewrite</em> a packet rather than only
 * pass or drop it. Three outcomes:
 *
 * <ul>
 *   <li>{@link #pass()}: let the packet continue unchanged (the legacy {@link PacketAction#PASS});
 *   <li>{@link #cancel()}: drop the packet (the legacy {@link PacketAction#CANCEL});
 *   <li>{@link #rewrite(Object)}: forward a <em>replacement</em> packet in place of the original.
 * </ul>
 *
 * <p>This is the superset of {@link PacketAction}: {@link #from(PacketAction)} maps the two legacy verdicts onto
 * the matching verdict here, so a listener that only implements the {@link PacketListener#onSend} /
 * {@link PacketListener#onReceive} pass/cancel methods behaves exactly as before: a listener opts into rewrite
 * by overriding {@link PacketListener#onSendVerdict} instead. The folding rule (see
 * {@link PacketListenerRegistry}) keeps {@code cancel} a veto that beats a rewrite, so an observe-only or
 * cancelling listener is never overridden by a rewriting one.
 *
 * <p>Rewrite is an <em>outbound</em> mechanism: the interceptor only honours a replacement on the server→client
 * write path (an inbound rewrite is meaningless here and is treated as a pass). The replacement is an opaque
 * {@link Object} (the same raw packet type the channel speaks in), so this type carries no packet knowledge.
 */
public sealed interface PacketVerdict permits PacketVerdict.Pass, PacketVerdict.Cancel, PacketVerdict.Rewrite {

    /** Let the packet continue unchanged. */
    static PacketVerdict pass() {
        return Pass.INSTANCE;
    }

    /** Drop the packet; it is not forwarded further along the pipeline. */
    static PacketVerdict cancel() {
        return Cancel.INSTANCE;
    }

    /** Forward {@code replacement} in place of the original packet. */
    static PacketVerdict rewrite(Object replacement) {
        return new Rewrite(replacement);
    }

    /** The verdict matching a legacy {@link PacketAction}: {@code PASS} -> {@link #pass()}, {@code CANCEL} -> {@link #cancel()}. */
    static PacketVerdict from(PacketAction action) {
        Objects.requireNonNull(action, "action");
        return action.cancels() ? cancel() : pass();
    }

    /** {@code true} if this verdict cancels (drops) the packet. */
    default boolean cancels() {
        return this instanceof Cancel;
    }

    /** The replacement packet if this is a {@link Rewrite}, otherwise {@code null}. */
    default @Nullable Object replacement() {
        return this instanceof Rewrite rewrite ? rewrite.replacement() : null;
    }

    /** The "let it through unchanged" verdict; a singleton because it carries no state. */
    final class Pass implements PacketVerdict {
        private static final Pass INSTANCE = new Pass();

        private Pass() {}
    }

    /** The "drop it" verdict; a singleton because it carries no state. */
    final class Cancel implements PacketVerdict {
        private static final Cancel INSTANCE = new Cancel();

        private Cancel() {}
    }

    /** The "forward this replacement instead" verdict, carrying the new packet to write downstream. */
    record Rewrite(Object replacement) implements PacketVerdict {
        public Rewrite {
            Objects.requireNonNull(replacement, "replacement");
        }
    }
}
