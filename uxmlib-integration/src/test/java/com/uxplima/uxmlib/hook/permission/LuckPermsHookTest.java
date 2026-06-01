package com.uxplima.uxmlib.hook.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Verifies the load-without-LuckPerms invariant: the hook reports empty rather than throwing. */
class LuckPermsHookTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void findIsEmptyWhenLuckPermsAbsent() {
        assertThat(LuckPermsHook.find()).isEmpty();
    }
}
