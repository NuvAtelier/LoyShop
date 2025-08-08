package com.snowgears.shop.integration.features;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.testsupport.BaseMockBukkitTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.scheduler.BukkitTask;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class ShopDestroyTest extends BaseMockBukkitTest {


    @Test
    void destroyOwn_op() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        // create the shop
        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 10, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());
        assertEquals("§7You have destroyed your selling shop.", waitForNextMessage(player), "Player should be sent dialog after destroying shop sign");
    }
    @Test
    void destroyOwn_permissions() {
        setConfig("usePerms", true);
        
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setName("Toby");
        player.setOp(false);
        player.addAttachment(getPlugin(), "shop.create.sell", true);
        player.addAttachment(getPlugin(), "shop.destroy", false);
        player.addAttachment(getPlugin(), "shop.operator", false);
        // create the shop
        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 18, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");
        PlayerSimulation simulation = new PlayerSimulation(player);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());
        assertEquals("§cYou are not authorized to destroy your own shops.", waitForNextMessage(player), "Player should be sent dialog denying own shop destroy without shop.destroy permission");
        
        player.addAttachment(getPlugin(), "shop.destroy", true);
        simulation.simulateBlockBreak(shop.getSignLocation().getBlock());
        assertEquals("§7You have destroyed your selling shop.", waitForNextMessage(player), "Player should be sent dialog after destroying shop sign");
    }

    @Test
    void destroyOther_noPermission() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        // create the shop
        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 12, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        PlayerMock playerTwo = server.addPlayer();
        playerTwo.setOp(false);
        PlayerSimulation simulationTwo = new PlayerSimulation(playerTwo);
        simulationTwo.simulateBlockBreak(shop.getSignLocation().getBlock());
        assertEquals("§cYou are not authorized to destroy other players shops.", waitForNextMessage(playerTwo), "Player two should be sent dialog denying shop destruction");
    }
    @Test
    void destroyOther_op() {
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setName("Steve");
        // create the shop
        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 14, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        PlayerMock playerTwo = server.addPlayer();
        playerTwo.setOp(true);
        PlayerSimulation simulationTwo = new PlayerSimulation(playerTwo);
        simulationTwo.simulateBlockBreak(shop.getSignLocation().getBlock());
        assertEquals("§7You have destroyed a selling shop owned by Steve.", waitForNextMessage(playerTwo), "Player two should be sent dialog allowing shop destruction");
    }

    @Test
    void destroyOther_permissions() {
        setConfig("usePerms", true);
        
        ServerMock server = getServer();
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setName("Toby");
        player.setOp(true);
        // create the shop
        AbstractShop shop = ShopCreationChestTest.createShop(server, getPlugin(), player, world, 16, 65, 10, new ItemStack(Material.DIRT), "sell", 8, "1");

        PlayerMock playerTwo = server.addPlayer();
        playerTwo.setOp(false);
        PlayerSimulation simulationTwo = new PlayerSimulation(playerTwo);
        simulationTwo.simulateBlockBreak(shop.getSignLocation().getBlock());
        
        PlayerMock playerThree = server.addPlayer();
        playerThree.setOp(false);
        playerThree.addAttachment(getPlugin(), "shop.destroy.other", true);
        PlayerSimulation simulationThree = new PlayerSimulation(playerThree);
        simulationThree.simulateBlockBreak(shop.getSignLocation().getBlock());
        assertEquals("§7You have destroyed a selling shop owned by Toby.", waitForNextMessage(playerThree), "Player three should be sent dialog allowing shop destruction");
    }
}

