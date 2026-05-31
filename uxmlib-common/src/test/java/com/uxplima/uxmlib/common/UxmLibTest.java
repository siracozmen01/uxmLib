package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UxmLibTest {

    @Test
    void exposesANonBlankVersion() {
        assertThat(UxmLib.VERSION).isNotBlank();
    }
}
