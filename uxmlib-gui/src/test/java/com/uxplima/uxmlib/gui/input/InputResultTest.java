package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputResultTest {

    @Test
    void submittedCarriesTheTypedText() {
        InputResult result = new InputResult.Submitted("hello");
        assertThat(result).isInstanceOf(InputResult.Submitted.class);
        assertThat(((InputResult.Submitted) result).text()).isEqualTo("hello");
    }

    @Test
    void cancelledIsASingleton() {
        // Null-argument rejection is a compile-time NullAway check under @NullMarked, not a runtime test.
        assertThat(InputResult.Cancelled.INSTANCE).isEqualTo(new InputResult.Cancelled());
    }
}
