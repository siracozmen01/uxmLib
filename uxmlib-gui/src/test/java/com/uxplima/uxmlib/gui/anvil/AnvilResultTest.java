package com.uxplima.uxmlib.gui.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnvilResultTest {

    @Test
    void submittedCarriesTheTypedText() {
        AnvilResult result = new AnvilResult.Submitted("hello");
        assertThat(result).isInstanceOf(AnvilResult.Submitted.class);
        assertThat(((AnvilResult.Submitted) result).text()).isEqualTo("hello");
    }

    @Test
    void cancelledIsASingleton() {
        // Null-argument rejection is a compile-time NullAway check under @NullMarked, not a runtime test.
        assertThat(AnvilResult.Cancelled.INSTANCE).isEqualTo(new AnvilResult.Cancelled());
    }
}
