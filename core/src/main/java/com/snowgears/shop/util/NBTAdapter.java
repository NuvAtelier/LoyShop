package com.snowgears.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import com.snowgears.shop.Shop;

public class NBTAdapter {
    private Shop plugin;
    private boolean useNBTAPIPlugin = false;
    private int errorCount = 0;
    
    public NBTAdapter(Shop shop) {
        plugin = shop;
        // Check if NBTAPI plugin is installed
        if (Bukkit.getPluginManager().getPlugin("NBTAPI") != null) {
            plugin.getLogger().helpful("[NBTAdapter] NBTAPI plugin is installed, using NBTAPI");
            useNBTAPIPlugin = true;
        } else {
            plugin.getLogger().helpful("[NBTAdapter] NBTAPI plugin is not installed, using built in NBTAPI library");
            useNBTAPIPlugin = false;
        }
    }

    public boolean haveErrorsOccured() {
        return errorCount > 0;
    }

    public String getNBTforItem(ItemStack item) {
        try {
            if (useNBTAPIPlugin) {
                return de.tr7zw.nbtapi.NBT.itemStackToNBT(item).toString();
            } else {
                return de.tr7zw.changeme.nbtapi.NBT.itemStackToNBT(item).toString();
            }
        } catch (Error | Exception e) { handleException(e.getMessage()); }
        // Error while getting NBT for item, return a dummy NBT string
        return "{count:1,id:\"minecraft:dirt\"}";
    }

    public void handleException(String message) {
        errorCount++;
        if (errorCount < 5){
            plugin.getLogger().severe("[NBTAdapter] Error while trying to use NBTAPI, NBTAPI might not be up to date for your Minecraft version! Some features of Shop will not work as expected!");
            if (useNBTAPIPlugin) {
                plugin.getLogger().severe("[NBTAdapter] Shop is attempting to use the installed NBTAPI Plugin.");
            } else {
                plugin.getLogger().severe("[NBTAdapter] Shop is using its built in NBTAPI library and may be out of date.");
            }
            plugin.getLogger().severe("[NBTAdapter] Please install the latest version of the NBTAPI plugin! https://www.spigotmc.org/resources/nbt-api.7939/");
            plugin.getLogger().severe("[NBTAdapter] Error message: " + message);
        }
    }
}
