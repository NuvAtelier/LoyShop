package com.snowgears.shop.testsupport;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.block.BlockFace;
import java.util.Collections;
import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.util.CurrencyType;
import com.snowgears.shop.util.NBTAdapter;
import com.snowgears.shop.util.ShopCreationUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;

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

    @BeforeEach
    void initServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Shop.class);

        server.getScheduler().waitAsyncTasksFinished();

        // Disable displays to avoid NMS/NBT code paths in tests
        setConfig("displayType", DisplayType.NONE);

        // Spy and stub NBTAdapter.getNBTforItem to return a static string
        NBTAdapter original = (NBTAdapter) getPluginField("nbtAdapter");
        if (original != null) {
            NBTAdapter spy = Mockito.spy(original);
            Mockito.doReturn("{count:1,id:\"minecraft:dirt\"}").when(spy).getNBTforItem(Mockito.any());
            setPluginField("nbtAdapter", spy);
        }
    }

    @AfterEach
    void tearDownServer() {
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

    // ---------- Test tooling helpers ----------
    protected static void sendChatMessage(PlayerMock player, String message) {
        AsyncPlayerChatEvent amountEvent = new AsyncPlayerChatEvent(true, player, message, Collections.emptySet());
        // server.getPluginManager().callEventAsynchronously(amountEvent);
        // server.getScheduler().executeAsyncEvent(amountEvent);
        try {
            server.getScheduler().executeAsyncEvent(amountEvent).get();
        } catch (Error | Exception e) {
            throw new RuntimeException("Failed to send chat message", e);
        }
    }

    protected static String waitForNextMessage(PlayerMock player) {
        String nextMessage = null;
        int attempts = 0;   
        while (nextMessage == null && attempts < 1000) {
            nextMessage = player.nextMessage();
            if (nextMessage == null) {
                server.getScheduler().performTicks(1);
                attempts++;
            }
        }
        System.out.println("ticks: " + attempts + ", message: `" + nextMessage + "`");
        return nextMessage;
    }

    protected static <T> T getPluginField(String fieldName) {
        try {
            var field = Shop.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(plugin);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field '" + fieldName + "' on Shop", e);
        }
    }

    protected static void setPluginField(String fieldName, Object value) {
        try {
            var field = Shop.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(plugin, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on Shop", e);
        }
    }

    protected static void setConfig(String fieldName, Object value) {
        setPluginField(fieldName, value);
    }

    protected static void setConfig(String fieldName, boolean value) {
        setPluginField(fieldName, value);
    }

    protected static void setConfig(String fieldName, int value) {
        setPluginField(fieldName, value);
    }

    protected static void setConfig(String fieldName, double value) {
        setPluginField(fieldName, value);
    }

    protected static <E extends Enum<E>> void setConfig(String fieldName, E value) {
        setPluginField(fieldName, value);
    }

    // Allow tests to stub calculateBlockFaceForSign to avoid MockBukkit material checks
    protected static void stubCalculateBlockFaceForSign(BlockFace face) {
        ShopCreationUtil original = getPluginField("shopCreationUtil");
        if (original != null && !MockUtil.isMock(original)) {
            ShopCreationUtil spy = Mockito.spy(original);
            Mockito.doReturn(face).when(spy).calculateBlockFaceForSign(Mockito.any(), Mockito.any(), Mockito.any());
            setPluginField("shopCreationUtil", spy);
        }
    }
    protected static void setupEconomy() {
        // Inject Economy mock if Vault currency is enabled
        if (plugin.getCurrencyType() == CurrencyType.VAULT) {
            Economy mockedEconomy = Mockito.mock(Economy.class);
            Mockito.when(mockedEconomy.getBalance(Mockito.any(org.bukkit.OfflinePlayer.class))).thenReturn(10_000.0);
            Mockito.when(mockedEconomy.withdrawPlayer(Mockito.any(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                    .thenAnswer(inv -> new EconomyResponse(inv.getArgument(1), 10_000.0, ResponseType.SUCCESS, "ok"));
            Mockito.when(mockedEconomy.depositPlayer(Mockito.any(org.bukkit.OfflinePlayer.class), Mockito.anyDouble()))
                    .thenAnswer(inv -> new EconomyResponse(inv.getArgument(1), 10_000.0, ResponseType.SUCCESS, "ok"));
            setPluginField("econ", mockedEconomy);
        } 
    }
}
