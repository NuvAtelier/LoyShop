package com.snowgears.shop.testsupport;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.snowgears.shop.Shop;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for MockBukkit based tests.
 * <p>
 * Brings up a single {@link ServerMock} once per test-class and tears it down afterwards.
 * Sub-classes can call {@link #getServer()} or {@link #getPlugin()}.
 * Example usage:
public class PluginLoadTest extends BaseMockBukkitTest {
    @Test
    void pluginShouldEnable() {
        assertNotNull(getPlugin(), "Plugin should be loaded by MockBukkit");
        assertTrue(getPlugin().isEnabled(), "Plugin should be enabled inside MockBukkit environment");
    }
}
 */
public abstract class BaseMockBukkitTest {

    private static ServerMock server;
    private static Shop plugin;

    @BeforeAll
    static void initServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Shop.class);
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
        server = null;
        plugin = null;
    }

    protected ServerMock getServer() {
        return server;
    }

    protected Shop getPlugin() {
        return plugin;
    }
}
