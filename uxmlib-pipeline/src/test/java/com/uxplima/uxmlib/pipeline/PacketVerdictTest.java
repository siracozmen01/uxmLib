package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class PacketVerdictTest {

    @Test
    void passNeitherCancelsNorCarriesAReplacement() {
        PacketVerdict pass = PacketVerdict.pass();
        assertThat(pass.cancels()).isFalse();
        assertThat(pass.replacement()).isNull();
    }

    @Test
    void cancelCancelsAndCarriesNoReplacement() {
        PacketVerdict cancel = PacketVerdict.cancel();
        assertThat(cancel.cancels()).isTrue();
        assertThat(cancel.replacement()).isNull();
    }

    @Test
    void rewriteCarriesItsReplacementAndDoesNotCancel() {
        Object replacement = new Object();
        PacketVerdict rewrite = PacketVerdict.rewrite(replacement);
        assertThat(rewrite.cancels()).isFalse();
        assertThat(rewrite.replacement()).isSameAs(replacement);
    }

    @Test
    void fromMapsTheLegacyActions() {
        assertThat(PacketVerdict.from(PacketAction.PASS).cancels()).isFalse();
        assertThat(PacketVerdict.from(PacketAction.PASS).replacement()).isNull();
        assertThat(PacketVerdict.from(PacketAction.CANCEL).cancels()).isTrue();
    }

    @Test
    void rewriteRejectsANullReplacement() {
        assertThatNullPointerException().isThrownBy(() -> PacketVerdict.rewrite(nullPacket()));
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the ctor guard fires.
    private static Object nullPacket() {
        return null;
    }
}
