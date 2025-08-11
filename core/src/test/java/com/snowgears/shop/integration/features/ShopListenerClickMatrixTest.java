package com.snowgears.shop.integration.features;

import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import com.snowgears.shop.util.ShopClickType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("integration")
public class ShopListenerClickMatrixTest extends BaseMockBukkitTest {

    private ServerMock server;
    private World world;
    private PlayerMock owner;

    @BeforeEach
    void setup() {
        server = getServer();
        world = server.addSimpleWorld("world");
        owner = server.addPlayer();
        owner.setOp(true);
        owner.setSneaking(false);
    }

    private AbstractShop createInitializedShopAt(Location chestLoc) {
        return ShopCreationChestTest.createShop(server, getPlugin(), owner, world, chestLoc, new ItemStack(Material.DIRT), "sell", 8, "1");
    }

    private AbstractShop spyAndReplace(AbstractShop real) {
        try {
            // Replace the registered shop in ShopHandler with a spy so we can verify executeClickAction invocations
            var handler = getPlugin().getShopHandler();
            Field f = handler.getClass().getDeclaredField("allShops");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Location, AbstractShop> map = (ConcurrentHashMap<Location, AbstractShop>) f.get(handler);
            AbstractShop spy = spy(real);
            // By default, have the spy return true for any executeClickAction so that ShopListener cancels events
            when(spy.executeClickAction(any(PlayerInteractEvent.class), any(ShopClickType.class))).thenReturn(true);
            map.put(real.getSignLocation(), spy);
            return spy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace shop with spy", e);
        }
    }

    // ---------- Sign click matrix ----------

    @Test
    void sign_rightClick_calls_RIGHT_CLICK_SIGN_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 10, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(false);

        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_BLOCK, owner.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.RIGHT_CLICK_SIGN));
        assertTrue(event.isCancelled(), "RIGHT_CLICK_SIGN should cancel when actionPerformed=true");
    }

    @Test
    void sign_leftClick_calls_LEFT_CLICK_SIGN_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 12, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(false);

        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.LEFT_CLICK_BLOCK, owner.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.LEFT_CLICK_SIGN));
        assertTrue(event.isCancelled(), "LEFT_CLICK_SIGN should cancel when actionPerformed=true");
    }

    @Test
    void sign_shiftRightClick_calls_SHIFT_RIGHT_CLICK_SIGN_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 14, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(true);

        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_BLOCK, owner.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.SHIFT_RIGHT_CLICK_SIGN));
        assertTrue(event.isCancelled(), "SHIFT_RIGHT_CLICK_SIGN should cancel when actionPerformed=true");
    }

    @Test
    void sign_shiftLeftClick_calls_SHIFT_LEFT_CLICK_SIGN_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 16, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(true);

        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.LEFT_CLICK_BLOCK, owner.getInventory().getItemInMainHand(), signBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.SHIFT_LEFT_CLICK_SIGN));
        assertTrue(event.isCancelled(), "SHIFT_LEFT_CLICK_SIGN should cancel when actionPerformed=true");
    }

    @Test
    void sign_offHand_ignored_noCall_noCancel() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 18, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(false);

        Block signBlock = shop.getSignLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_BLOCK, owner.getInventory().getItemInOffHand(), signBlock, BlockFace.NORTH, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(event);

        Mockito.verify(spy, Mockito.never()).executeClickAction(any(PlayerInteractEvent.class), any(ShopClickType.class));
        assertFalse(event.isCancelled(), "OFF_HAND interactions should be ignored");
    }

    // ---------- Chest click matrix ----------

    @Test
    void chest_rightClick_ownerSneaking_calls_SHIFT_RIGHT_CLICK_CHEST_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 20, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(true);

        Block chestBlock = shop.getChestLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_BLOCK, owner.getInventory().getItemInMainHand(), chestBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.SHIFT_RIGHT_CLICK_CHEST));
        assertTrue(event.isCancelled(), "SHIFT_RIGHT_CLICK_CHEST should cancel when actionPerformed=true");
    }

    @Test
    void chest_leftClick_owner_calls_LEFT_CLICK_CHEST_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 22, 65, 10));
        AbstractShop spy = spyAndReplace(shop);
        owner.setSneaking(false);

        Block chestBlock = shop.getChestLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.LEFT_CLICK_BLOCK, owner.getInventory().getItemInMainHand(), chestBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.LEFT_CLICK_CHEST));
        assertTrue(event.isCancelled(), "LEFT_CLICK_CHEST should cancel when actionPerformed=true");
    }

    @Test
    void chest_rightClick_nonOwner_calls_RIGHT_CLICK_CHEST_andCancels() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 24, 65, 10));
        AbstractShop spy = spyAndReplace(shop);

        // Non-owner player
        PlayerMock stranger = server.addPlayer();
        stranger.setOp(false);

        Block chestBlock = shop.getChestLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(stranger, Action.RIGHT_CLICK_BLOCK, stranger.getInventory().getItemInMainHand(), chestBlock, BlockFace.NORTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        verify(spy).executeClickAction(any(PlayerInteractEvent.class), Mockito.eq(ShopClickType.RIGHT_CLICK_CHEST));
        assertTrue(event.isCancelled(), "Non-owner RIGHT_CLICK_CHEST should be cancelled");
    }

    @Test
    void chest_offHand_ignored_noCall_noCancel() {
        AbstractShop shop = createInitializedShopAt(new Location(world, 26, 65, 10));
        AbstractShop spy = spyAndReplace(shop);

        Block chestBlock = shop.getChestLocation().getBlock();
        PlayerInteractEvent event = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_BLOCK, owner.getInventory().getItemInOffHand(), chestBlock, BlockFace.NORTH, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(event);

        Mockito.verify(spy, Mockito.never()).executeClickAction(any(PlayerInteractEvent.class), any(ShopClickType.class));
        assertFalse(event.isCancelled(), "OFF_HAND interactions should be ignored for chest as well");
    }
}


