package com.uxplima.uxmlib.hook.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Verifies the load-without-LuckPerms invariant (the hook reports empty rather than throwing) and the pure
 * offline-meta extraction that the {@code *Async} accessors map a loaded user through.
 */
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

    @Test
    void extractOfNullUserIsEmpty() {
        assertThat(LuckPermsHook.extract(null, CachedMetaData::getPrefix)).isEmpty();
    }

    @Test
    void extractReadsTheRequestedMetaField() {
        CachedMetaData meta = mock(CachedMetaData.class);
        when(meta.getPrefix()).thenReturn("[VIP] ");
        when(meta.getSuffix()).thenReturn(null);
        CachedDataManager data = mock(CachedDataManager.class);
        when(data.getMetaData()).thenReturn(meta);
        User user = mock(User.class);
        when(user.getCachedData()).thenReturn(data);

        assertThat(LuckPermsHook.extract(user, CachedMetaData::getPrefix)).contains("[VIP] ");
        assertThat(LuckPermsHook.extract(user, CachedMetaData::getSuffix)).isEmpty();
    }

    @Test
    void groupNamesAreReadFromTheInheritanceNodes() {
        InheritanceNode vip = mock(InheritanceNode.class);
        when(vip.getGroupName()).thenReturn("vip");
        InheritanceNode staff = mock(InheritanceNode.class);
        when(staff.getGroupName()).thenReturn("staff");

        assertThat(LuckPermsHook.groupsOf(List.of(vip, staff))).containsExactly("vip", "staff");
    }

    @Test
    void groupNamesFromNoInheritanceNodesIsEmpty() {
        assertThat(LuckPermsHook.groupsOf(List.<InheritanceNode>of())).isEmpty();
    }

    @Test
    void inheritanceNodeTypeIsTheOneQueried() {
        // Guards the helper against drifting off the inheritance node type the groups list is built from.
        NodeType<InheritanceNode> type = LuckPermsHook.inheritanceType();
        Node node = mock(InheritanceNode.class);
        assertThat(type.matches(node)).isTrue();
    }

    @Test
    void extractWrapsAPresentValue() {
        CachedMetaData meta = mock(CachedMetaData.class);
        when(meta.getPrimaryGroup()).thenReturn("admin");
        CachedDataManager data = mock(CachedDataManager.class);
        when(data.getMetaData()).thenReturn(meta);
        User user = mock(User.class);
        when(user.getCachedData()).thenReturn(data);

        Optional<String> group = LuckPermsHook.extract(user, CachedMetaData::getPrimaryGroup);
        assertThat(group).contains("admin");
    }
}
