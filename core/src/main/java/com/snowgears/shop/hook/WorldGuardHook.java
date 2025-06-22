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

import java.util.List;

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
        private static FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        public static void registerAllowShopFlag(Shop plugin) {
            Bukkit.getLogger().log(Level.INFO,"[Shop] Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'");
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

            // Use new configurable flag checking system
            boolean flagCheckResult = checkWorldGuardFlags(query, wgPlugin.wrapPlayer(player), loc, 
                Shop.getPlugin().getWorldGuardCreateShopHardAllowFlags(),
                Shop.getPlugin().getWorldGuardCreateShopDenyFlags(),
                Shop.getPlugin().getWorldGuardCreateShopAllowFlags(),
                Shop.getPlugin().getWorldGuardCreateShopDefaultAction());

            // If flag checks fail, deny shop creation
            if (!flagCheckResult) {
                return false;
            }

            // If flag checks pass, check if we need the allow-shop flag
            if (Shop.getPlugin().getWorldGuardRequireAllowShopFlag()) {
                // Allow shops ONLY in regions with the shop flag set
                return areShopsAllowedInRegion;
            }
            
            // If we don't require the shop flag, allow shop creation
            return true;
        }

        /**
         * Check WorldGuard flags using the new configurable four-tier system.
         * 
         * @param query The WorldGuard region query
         * @param player The player to check flags for
         * @param loc The location to check
         * @param hardAllowFlags List of flags that always allow if set to ALLOW
         * @param denyFlags List of flags that deny if set to DENY
         * @param allowFlags List of flags that allow if set to ALLOW (after deny checks)
         * @param defaultAction What to do if no flags trigger ("ALLOW" or "DENY")
         * @return true if the action should be allowed, false otherwise
         */
        private static boolean checkWorldGuardFlags(RegionQuery query, LocalPlayer player, Location loc,
                List<String> hardAllowFlags, List<String> denyFlags, 
                List<String> allowFlags, String defaultAction) {
            
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            
            // Tier 1: Hard Allow Flags - Always allow if any are set to ALLOW
            for (String flagName : hardAllowFlags) {
                StateFlag flag = getStateFlagByName(flagName);
                if (flag != null) {
                    StateFlag.State state = query.queryState(wgLoc, player, flag);
                    if (state == StateFlag.State.ALLOW) {
                        return true; // Hard allow - bypass all other checks
                    }
                }
            }
            
            // Tier 2: Deny Flags - Block if any are set to DENY
            for (String flagName : denyFlags) {
                StateFlag flag = getStateFlagByName(flagName);
                if (flag != null) {
                    StateFlag.State state = query.queryState(wgLoc, player, flag);
                    if (state == StateFlag.State.DENY) {
                        return false; // Explicit deny
                    }
                }
            }
            
            // Tier 3: Allow Flags - Allow if any are set to ALLOW
            for (String flagName : allowFlags) {
                StateFlag flag = getStateFlagByName(flagName);
                if (flag != null) {
                    StateFlag.State state = query.queryState(wgLoc, player, flag);
                    if (state == StateFlag.State.ALLOW) {
                        return true; // Explicit allow
                    }
                }
            }
            
            // Tier 4: Default Action - No flags triggered, use default
            return "ALLOW".equalsIgnoreCase(defaultAction);
        }

        /**
         * Get a WorldGuard StateFlag by name.
         * 
         * @param flagName The name of the flag (e.g., "BUILD", "CHEST_ACCESS")
         * @return The StateFlag instance, or null if not found
         */
        private static StateFlag getStateFlagByName(String flagName) {
            if (flagName == null || flagName.trim().isEmpty()) {
                return null;
            }
            
            try {
                Flag<?> flag = registry.get(flagName);
                if (flag instanceof StateFlag) {
                    return (StateFlag) flag;
                }
            } catch (Exception e) {
                // Flag not found, log warning
                Bukkit.getLogger().warning("WorldGuard flag '" + flagName + "' not found or not a StateFlag");
            }
            
            return null;
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
            Plugin wgPlugin = getPlugin();
            if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
            
            WorldGuardPlugin worldGuardPlugin = (WorldGuardPlugin) wgPlugin;
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            
            // Use new configurable flag checking system for shop usage
            return Internal.checkWorldGuardFlags(query, worldGuardPlugin.wrapPlayer(player), location,
                Shop.getPlugin().getWorldGuardUseShopHardAllowFlags(),
                Shop.getPlugin().getWorldGuardUseShopDenyFlags(),
                Shop.getPlugin().getWorldGuardUseShopAllowFlags(),
                Shop.getPlugin().getWorldGuardUseShopDefaultAction());
        } catch (NoClassDefFoundError ignore) {
        }
        return true;
    }

    public static boolean isRegionOwner(Player player, Location location) {
        if (!Shop.getPlugin().worldGuardExists()) {
            return false;
        }
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld());
            BlockVector3 vLoc = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            if(regions == null || regions.size() == 0)
                return false;

            ApplicableRegionSet set = regions.getApplicableRegions(vLoc);
            if (set.size() == 0)
                return false;

            if(regions.getApplicableRegions(vLoc).isOwnerOfAll(localPlayer)){
                return true;
            }
        } catch (NoClassDefFoundError ignore) {
        }
        return false;
    }
}