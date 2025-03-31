package com.snowgears.shop.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public class WorldGuardHook {

    public static final String PLUGIN_NAME = "WorldGuard";
    // This flag got originally registered by WorldGuard itself, but this is no longer the case. Other plugins are
    // supposed to register it themselves. One such plugin is for example ChestShop. To not rely on other plugins for
    // registering this flag, we will register it ourselves if no other plugin has registered it yet.
    private static final String FLAG_ALLOW_SHOP = "allow-shop";

    public static Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    // Note: WorldGuard only allows registering flags before it got enabled.
    public static void registerAllowShopFlag() {
        if (getPlugin() == null) return; // WorldGuard is not loaded
            Internal.registerAllowShopFlag();
    }

    // Separate class that gets only accessed if WorldGuard is present. Avoids class loading issues.
    private static class Internal {

        public static void registerAllowShopFlag() {
            Shop.getPlugin().getLogger().debug("Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'.");
            try {
                StateFlag allowShopFlag = new StateFlag(FLAG_ALLOW_SHOP, false);
                WorldGuard.getInstance().getFlagRegistry().register(allowShopFlag);
            } catch (FlagConflictException | IllegalStateException e) {
                // Another plugin has probably already registered this flag,
                // or this plugin got hard reloaded by some plugin manager plugin.
                Bukkit.getLogger().log(Level.SEVERE,"Couldn't register WorldGuard flag '" + FLAG_ALLOW_SHOP + "': " + e.getMessage());
            }
        }

        public static boolean isShopAllowed(Plugin worldGuardPlugin, Player player, Location loc) {
            assert worldGuardPlugin instanceof WorldGuardPlugin && worldGuardPlugin.isEnabled() && player != null && loc != null;
            WorldGuardPlugin wgPlugin = (WorldGuardPlugin) worldGuardPlugin;
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

            // Check if we are not in a region.
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));
            if (regions.size() == 0) {
                // If we have no regions here, then we need to check if we are requiring the `allow-shops` worldguard flag
                if (Shop.getPlugin().hookWorldGuard()) {
                    // Allow shops ONLY in regions with the shop flag set
                    return false;
                }
                // If we are not requiring `allow-shops` and we are not inside a region, then allow shop creation
                return true;
            }

            // Check if shop flag is set:
            boolean allowShopFlag = false; // false if unset or disallowed

            // Get shop flag:
            Flag<?> shopFlag = WorldGuard.getInstance().getFlagRegistry().get(FLAG_ALLOW_SHOP);
            if (shopFlag != null) {
                // Check if shop flag is set:
                if (shopFlag instanceof StateFlag) {
                    allowShopFlag = query.testState(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), (StateFlag) shopFlag);
                } else if (shopFlag instanceof BooleanFlag) {
                    // Value might be null:
                    Boolean shopFlagValue = query.queryValue(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), (BooleanFlag) shopFlag);
                    allowShopFlag = (Boolean.TRUE.equals(shopFlagValue));
                } else {
                    // Unknown flag type, assume unset.
                }
            } else {
                // Shop flag doesn't exist, assume unset.
            }

            // Check if we should deny the player from creating a shop based on other WG Flags
            // We cannot create a shop if we cannot Build (add a sign), or if we cannot interact with a chest, and PASSTHROUGH is not allowed
            StateFlag.State passthroughFlag = query.queryState(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), Flags.PASSTHROUGH);
            StateFlag.State buildFlag = query.queryState(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), Flags.BUILD);
            StateFlag.State chestAccessFlag = query.queryState(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), Flags.CHEST_ACCESS);

            // If building or chest access is explicitly denied, then they are not allowed to make a shop here
            if (buildFlag == StateFlag.State.DENY || chestAccessFlag == StateFlag.State.DENY) {
                return false;
            }
            // If passthrough is explicitly allowed, then we are allowed to create our shop
            // If we alternatively explicitly have explicit build permission and chest access permission, then we are allowed to create our shop
            else if (passthroughFlag == StateFlag.State.ALLOW || (buildFlag == StateFlag.State.ALLOW && chestAccessFlag == StateFlag.State.ALLOW)) {
                if (Shop.getPlugin().hookWorldGuard()) {
                    // Allow shops ONLY in regions with the shop flag set
                    return allowShopFlag;
                }
                // If we are not required to have the shop flag, just default return true
                return true;
            }

            // If we didn't match the flag requirements above, default return false
            return false;
        }

        private Internal() {
        }
    }

    public static boolean canCreateShop(Player player, Location location) {
        // Check if the WorldGuard plugin even exists on the server
        if (!Shop.getPlugin().worldGuardExists()) {
            return true;
        }
        if (player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        try {
            Plugin wgPlugin = getPlugin();
            if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
            return Internal.isShopAllowed(wgPlugin, player, location);
        } catch (Exception | NoClassDefFoundError ignore) {
        }
        return true;
    }

    public static boolean canUseShop(Player player, Location location) {
        if (!Shop.getPlugin().worldGuardExists()) {
            return true;
        }
        if (player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
            return true;
        }
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld());
            BlockVector3 vLoc = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            return regions == null || regions.getApplicableRegions(vLoc).queryState(localPlayer, Flags.USE) != StateFlag.State.DENY;
        } catch (NoClassDefFoundError ignore) {
        }
        return true;
    }

    public static boolean isRegionOwner(Player player, Location location) {
        if (!Shop.getPlugin().worldGuardExists()) {
            return false;
        }
        try {
//            System.out.println(player.getName()+" - checking if this player is a region owner at "+ UtilMethods.getCleanLocation(location, true));
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld());
            BlockVector3 vLoc = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            if(regions == null || regions.size() == 0)
                return false;

            ApplicableRegionSet set = regions.getApplicableRegions(vLoc);
            if (set.size() == 0)
                return false;

//            for(ProtectedRegion region : set.getRegions()){
//                System.out.println("    "+region.getId()+" - owner: "+region.getOwners().contains(player.getUniqueId()));
//            }

            if(regions.getApplicableRegions(vLoc).isOwnerOfAll(localPlayer)){
//                System.out.println(player.getName()+" - was a owner of all regions at that location");
                return true;
            }
        } catch (NoClassDefFoundError ignore) {
        }
        return false;
    }
}