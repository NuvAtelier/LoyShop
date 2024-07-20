package com.snowgears.shop.util;

import com.snowgears.shop.Shop;

import java.util.logging.Level;

public class NMSBullshitHandler {

    private Shop plugin;
    //for use with version and nms stuff
    private String nmsVersionString;
    private double serverVersion;

    private Class<?> craftItemStackClass;
    private Class<?> craftWorldClass;
    private Class<?> craftPlayerClass;
    private Class<?> enumDirectionClass;

    public NMSBullshitHandler(Shop plugin){
        this.plugin = plugin;
        init();
    }

    public void init() {
        String mcVersion = plugin.getServer().getClass().getPackage().getName();
        Shop.getPlugin().getLogger().log(Level.FINE, "mcVersion: " + mcVersion);

        String versionString = "";

        // Check if we are on Paper 1.20.5 or later, it will not include the
        // CB relocation version (i.e. "1_20_R3")
        if (!mcVersion.equals("org.bukkit.craftbukkit")) {
            Shop.getPlugin().getLogger().log(Level.WARNING, "Minecraft version is old, loaded version is: " + mcVersion);

            // If we do not have direct access to the version, extract it and load it
            // and set the version string to include the cb relocation string
            nmsVersionString = mcVersion.substring(mcVersion.lastIndexOf('.') + 2);
            Shop.getPlugin().getLogger().log(Level.FINE, "nmsVersionString: " + nmsVersionString);

            serverVersion = Double.parseDouble(plugin.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3].replace("_R", ".").replaceAll("[rvV_]*", ""));
            versionString = (".v" + String.valueOf(this.getServerVersion()).charAt(0) + "_" + String.valueOf(this.getServerVersion()).substring(1)).replace(".", "_R");
        }

        // log the server version we are on, it will be empty in versions 1.20.5 and later
        Shop.getPlugin().getLogger().log(Level.FINE, "versionString: " + versionString);
        Shop.getPlugin().getLogger().log(Level.FINE, "Server Version: " + this.getServerVersion());
        Shop.getPlugin().getLogger().log(Level.FINE, "Is Server Version over 117.0D: " + (Math.floor(this.getServerVersion()) >= 117.0D));


        try {
            this.craftItemStackClass = Class.forName("org.bukkit.craftbukkit" + versionString + ".inventory.CraftItemStack");
            if (Math.floor(this.getServerVersion()) >= 117.0D || this.getServerVersion() == 0) {
                this.craftWorldClass = Class.forName("org.bukkit.craftbukkit" + versionString + ".CraftWorld");
                this.craftPlayerClass = Class.forName("org.bukkit.craftbukkit" + versionString + ".entity.CraftPlayer");
                //this.craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + versionString + ".entity.CraftPlayer");

                // java.lang.ClassNotFoundException: net.minecraft.server.v1_17_R1.ItemStack

                Shop.getPlugin().getLogger().log(Level.FINE, "CraftItemStack: " + this.craftItemStackClass.toString());
                Shop.getPlugin().getLogger().log(Level.FINE, "CraftWorld: " + this.craftWorldClass.toString());
                Shop.getPlugin().getLogger().log(Level.FINE, "CraftPlayer: " + this.craftPlayerClass.toString());
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            Shop.getPlugin().getLogger().log(Level.SEVERE, "Unable to retrieve a NMS class used for NBT data.");
        }
    }

    public String getNmsVersion(){
        return nmsVersionString;
    }

    public double getServerVersion() {
        return this.serverVersion;
    }


    public Class<?> getCraftItemStackClass() {
        return craftItemStackClass;
    }

    public Class<?> getCraftWorldClass() {
        return craftWorldClass;
    }

    public Class<?> getCraftPlayerClass() {
        return craftPlayerClass;
    }

}
