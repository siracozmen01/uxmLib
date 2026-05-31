package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SkullDataTest {

    @Test
    void buildsEachVariant() {
        UUID id = UUID.randomUUID();
        assertThat(SkullData.ofUuid(id)).isEqualTo(new SkullData.ByUuid(id));
        assertThat(SkullData.ofName("Notch")).isEqualTo(new SkullData.ByName("Notch"));
        assertThat(SkullData.ofTexture("abc123")).isEqualTo(new SkullData.ByTexture("abc123"));
    }

    @Test
    void rejectsBlankNameAndTexture() {
        assertThatThrownBy(() -> SkullData.ofName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SkullData.ofTexture("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> SkullData.ofUuid(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SkullData.ofName(null)).isInstanceOf(NullPointerException.class);
    }
}
