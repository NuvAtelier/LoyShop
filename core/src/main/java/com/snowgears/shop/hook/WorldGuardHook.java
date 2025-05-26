package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

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
        if (getPlugin() == null) {
            Bukkit.getLogger().log(Level.WARNING, "[Shop] Cannot register WorldGuard flag - WorldGuard is not loaded");
            return;
        }

        try {
            Internal.registerAllowShopFlag(Shop.getPlugin());
        } catch (Exception | NoClassDefFoundError e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Shop] Failed to register WorldGuard flag due to unexpected error: " + e.getMessage());
        }
    }

    // Separate class that gets only accessed if WorldGuard is present. Avoids class loading issues.
    private static class Internal {
        private static StateFlag allowShopFlag;
        private static BooleanFlag deprecated_boolean_allowShopFlag;

        public static void registerAllowShopFlag(Shop plugin) {
            Bukkit.getLogger().log(Level.INFO,"[Shop] Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            try {
                // Create a new state flag with the name FLAG_ALLOW_SHOP, defaulting to false
                StateFlag flag = new StateFlag(FLAG_ALLOW_SHOP, false);
                registry.register(flag);
                // only set our field if there was no error
                allowShopFlag = flag;
                Bukkit.getLogger().log(Level.INFO,"[Shop] Successfully registered WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
            } catch (FlagConflictException e) {
                // some other plugin registered a flag by the same name already.
                // you can use the existing flag, but this may cause conflicts - be sure to check type
                Flag<?> existing = registry.get(FLAG_ALLOW_SHOP);
                if (existing instanceof StateFlag) {
                    allowShopFlag = (StateFlag) existing;
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag already registered, reusing StateFlag: '" + FLAG_ALLOW_SHOP + "'");
                }
                // Might be legacy flag, but we can still use it.
                else if (existing instanceof BooleanFlag) {
                    deprecated_boolean_allowShopFlag = (BooleanFlag) existing;
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag already registered, reusing BooleanFlag: '" + FLAG_ALLOW_SHOP + "' | Using deprecated 'BooleanFlag' (true/false), please update your regions to use a StateFlag (allow/deny)");
                }
                else {
                    // types don't match - this is bad news! some other plugin conflicts with you
                    // hopefully this never actually happens
                    Bukkit.getLogger().log(Level.SEVERE,"[Shop] Error while attempting to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "', the flag will not be enforced! Another plugin might have already registered the flag with a different type. '" + FLAG_ALLOW_SHOP + "' must be a StateFlag, but is a '" + existing.getClass().getName() + "'! | " + e.getMessage());
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE,"[Shop] Unknown Error while attempting to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "' | " + e.getMessage());
            }

            // Verify registration was successful
            if (allowShopFlag == null && deprecated_boolean_allowShopFlag == null) {
                Bukkit.getLogger().log(Level.SEVERE,"[Shop] Unable to register WorldGuard flag '" + FLAG_ALLOW_SHOP + "', the flag will not be enforced!");
            } else {
                // Additional verification - check if the flag is actually in the registry
                Flag<?> verifyFlag = registry.get(FLAG_ALLOW_SHOP);
                if (verifyFlag == null) {
                    Bukkit.getLogger().log(Level.SEVERE,"[Shop] WorldGuard flag '" + FLAG_ALLOW_SHOP + "' registration verification failed - flag not found in registry!");
                    allowShopFlag = null;
                    deprecated_boolean_allowShopFlag = null;
                } else {
                    Bukkit.getLogger().log(Level.INFO,"[Shop] WorldGuard flag '" + FLAG_ALLOW_SHOP + "' registration verified successfully");
                }
            }
        }

        public static boolean isShopAllowed(Plugin worldGuardPlugin, Player player, Location loc) {
            assert worldGuardPlugin instanceof WorldGuardPlugin && worldGuardPlugin.isEnabled() && player != null && loc != null;
            WorldGuardPlugin wgPlugin = (WorldGuardPlugin) worldGuardPlugin;
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

            // Always query the region, even if the region count is 0. 
            // This is because there is still a global region we need to check

            // Check if shop flag is set for the region:
            boolean areShopsAllowedInRegion = false; // false if unset or disallowed

            // Get shop flag:
            if (allowShopFlag != null) {
                areShopsAllowedInRegion = query.testState(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), (StateFlag) allowShopFlag);
            } 
            // Check if the server might be using the deprecated boolean flag type
            else if (deprecated_boolean_allowShopFlag != null) {
                // Value might be null:
                Boolean shopFlagValue = query.queryValue(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), (BooleanFlag) deprecated_boolean_allowShopFlag);
                areShopsAllowedInRegion = (Boolean.TRUE.equals(shopFlagValue));
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
            // If we alternatively explicitly have explicit build permission and chest access permission, 
            // then we are allowed to create our shop.
            // --> Note, since we have Build permission, we also have Chest Access permission by inheritance.
            else if (passthroughFlag == StateFlag.State.ALLOW || buildFlag == StateFlag.State.ALLOW) {
                if (Shop.getPlugin().hookWorldGuard()) {
                    // Allow shops ONLY in regions with the shop flag set
                    return areShopsAllowedInRegion;
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