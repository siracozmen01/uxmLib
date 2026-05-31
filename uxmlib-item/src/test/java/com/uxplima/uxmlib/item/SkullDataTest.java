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
        // Null inputs are ruled out at compile time by NullAway (@NullMarked), so only the runtime
        // value checks (blank name / texture) are exercised here.
        assertThatThrownBy(() -> SkullData.ofName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SkullData.ofTexture("")).isInstanceOf(IllegalArgumentException.class);
    }
}
