package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Test
    void ofUrlWrapsUrlInABase64Envelope() {
        SkullData.ByTexture skull = (SkullData.ByTexture) SkullData.ofUrl("http://textures.minecraft.net/texture/abc");
        String decoded = new String(Base64.getDecoder().decode(skull.base64()), StandardCharsets.UTF_8);
        assertThat(decoded).contains("\"url\":\"http://textures.minecraft.net/texture/abc\"");
    }

    @Test
    void parseRoutesDashedAndUndashedUuids() {
        UUID id = UUID.randomUUID();
        assertThat(SkullData.parse(id.toString())).isEqualTo(new SkullData.ByUuid(id));
        assertThat(SkullData.parse(id.toString().replace("-", ""))).isEqualTo(new SkullData.ByUuid(id));
    }

    @Test
    void parseRoutesUrlsToTextures() {
        assertThat(SkullData.parse("https://textures.minecraft.net/texture/abc"))
                .isInstanceOf(SkullData.ByTexture.class);
    }

    @Test
    void parseRoutesLongBase64ToTextures() {
        String texture =
                ((SkullData.ByTexture) SkullData.ofUrl("http://textures.minecraft.net/texture/abcdef")).base64();
        assertThat(texture.length()).isGreaterThanOrEqualTo(60);
        assertThat(SkullData.parse(texture)).isEqualTo(new SkullData.ByTexture(texture));
    }

    @Test
    void parseRoutesPlainNames() {
        assertThat(SkullData.parse("Notch")).isEqualTo(new SkullData.ByName("Notch"));
    }

    @Test
    void parseRejectsBlank() {
        assertThatThrownBy(() -> SkullData.parse("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
