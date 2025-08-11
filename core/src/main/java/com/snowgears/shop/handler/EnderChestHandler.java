package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class EnderChestHandler {

    private Shop plugin;
    private HashMap<UUID, Inventory> enderChestInventoryCache = new HashMap<>();

    public EnderChestHandler(Shop plugin){
        this.plugin = plugin;
    }

    public Inventory getInventory(OfflinePlayer player){
        //if player is online, just return their actual enderchest inventory
        if(player.getPlayer() != null) {
            return player.getPlayer().getEnderChest();
        }

        //check if cache has the inventory stored
        if(enderChestInventoryCache.containsKey(player.getUniqueId())){
            return enderChestInventoryCache.get(player.getUniqueId());
        }

        //if player is offline, load their enderchest inventory with NBT API
        File playerDatafolder = new File("world/playerdata");
        if (!playerDatafolder.isDirectory())
            return null;

        File[] files = playerDatafolder.listFiles();
        if (files == null)
            return null;

        try {
            for (File file : files) {
                if (file.getName().equalsIgnoreCase(player.getUniqueId()+".dat")) {
                    Inventory tempEnderEnv = plugin.getNBTAdapter().getEnderChestNBT(file);
                    enderChestInventoryCache.put(player.getUniqueId(), tempEnderEnv);
                    return tempEnderEnv;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unable to load EnderChest inventory! There might be an issue with EnderChestHandler/NBTAPI.");
        }
        return null;
    }

    public void saveInventory(final OfflinePlayer player){
        //if player is online, do nothing. Their inventory has already been saved when adding or removing items from it
        Player onlinePlayer = player.getPlayer();
        if(onlinePlayer != null){
           return;
        }

        //if there is no cache, that means the players .dat inventory was never loaded in so no need to save it
        if(!enderChestInventoryCache.containsKey(player.getUniqueId())){
            return;
        }

        Inventory playerEnderInventory = enderChestInventoryCache.get(player.getUniqueId());

        //if player is offline, load their enderchest inventory with NBT API
        File playerDatafolder = new File("world/playerdata");
        if (!playerDatafolder.isDirectory())
            return;

        File[] files = playerDatafolder.listFiles();
        if (files == null)
            return;

        //if player is offline, save NBT data
        try {
            for (File file : files) {
                if (file.getName().equalsIgnoreCase(player.getUniqueId()+".dat")) {
                    plugin.getNBTAdapter().saveEnderChestNBT(file, playerEnderInventory);
                    //after you have saved, remove the inventory from the cache
                    if(enderChestInventoryCache.containsKey(player.getUniqueId())) {
                        enderChestInventoryCache.remove(player.getUniqueId());
                    }
                    return;
                }
            }
        } catch (Exception e) {
            Shop.getPlugin().getLogger().log(Level.SEVERE, "Unable to save enderchest to file!");
        }

    }
}
