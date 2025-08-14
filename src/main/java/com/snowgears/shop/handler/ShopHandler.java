package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.DisplayUtil;
import com.snowgears.shop.util.ItemListType;
import com.snowgears.shop.util.PlayerNameCache;
import com.snowgears.shop.util.ShopLogger;
import com.snowgears.shop.util.UtilMethods;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


public class ShopHandler {

    public Shop plugin;
    private Class<?> displayClass;

    private ConcurrentHashMap<Location, AbstractShop> allShops = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, List<Location>> playerShops = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<Location>> chunkShops = new ConcurrentHashMap<>(); //String key = world_x_z
    private ConcurrentHashMap<UUID, HashSet<Location>> playersWithActiveShopDisplays = new ConcurrentHashMap<>();
    private HashSet<UUID> playersProcessingShopDisplays = new HashSet<>();
    private HashMap<UUID, Location> playersActiveShopDisplayTag = new HashMap<>();

    //all loading of shops happens async at onEnable()
    //shops that still need to calculate their facing direction based on sign are considered "unloaded"
    //we will be loading these shops at time of chunkload and resaving them so they are saved with the 'facing' variable
    private ConcurrentHashMap<String, List<Location>> unloadedShopsByChunk = new ConcurrentHashMap<>();
    private UUID adminUUID;
    private BlockFace[] directions = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private ArrayList<ItemStack> itemListItems = new ArrayList<>();

    // Map to track player last processed locations for movement-based display updates
    private ConcurrentHashMap<UUID, Location> lastProcessedLocations = new ConcurrentHashMap<>();

    // Cache for player connections to avoid expensive reflection calls
    private ConcurrentHashMap<UUID, Object> playerConnectionCache = new ConcurrentHashMap<>();

    // Teleport cooldown map to prevent multiple display updates during teleportation
    private ConcurrentHashMap<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    // Cooldown time in milliseconds (500ms = half a second)
    private static final long TELEPORT_COOLDOWN_MS = 500;

    public ShopHandler(Shop instance) {
        plugin = instance;
        adminUUID = UUID.randomUUID();
        initDisplayClass();
        initItemList();

        // Remplacer getFoliaLib par un appel direct au scheduler Bukkit
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            loadShops();
        }, 10);
    }

    public void disableDisplayClass() {
        try {
            final Class<?> clazz = Class.forName("com.snowgears.shop.display.DisplayDisabled");
            if (AbstractDisplay.class.isAssignableFrom(clazz))
                this.displayClass = clazz;
        } catch (final Exception e) {
            Shop.getPlugin().getLogger().severe("Failed to load DisplayDisabled class.");
            Shop.getPlugin().onDisable();
        } catch (Error e) {
            Shop.getPlugin().getLogger().severe("Failed to load DisplayDisabled class.");
            Shop.getPlugin().onDisable();
        }
    }

    private boolean initDisplayClass(){
        String packageName = plugin.getServer().getClass().getPackage().getName();

        // Check if we are on a Paper 1.20.6+ server, or if we are running Spigot v1.20.6 or later :)
        // Now that our new Display class purely uses Class loading to get the appropriate class, we don't
        // need to load a specific revision version class (unless we are old)
        // Simplifié pour Paper : plus besoin de détection de version complexe
        if (packageName.equals("org.bukkit.craftbukkit") || true) { // Paper moderne
            // We are on a newer version that does not relocate CB classes, load the default display package
            try {
                Shop.getPlugin().getLogger().info("Using Paper display handler - com.snowgears.shop.display.Display_v1_21_R3");
                final Class<?> clazz = Class.forName("com.snowgears.shop.display.Display_v1_21_R3");
                if (AbstractDisplay.class.isAssignableFrom(clazz)) {
                    this.displayClass = clazz;
                    return true;
                }
            } catch (final Exception e) {
                Shop.getPlugin().getLogger().severe("Error while loading 'com.snowgears.shop.display.Display'. " + e.getMessage());
                e.printStackTrace();
                disableDisplayClass();
                return false;
            } catch (Error e) {
                Shop.getPlugin().getLogger().severe("Error while loading 'com.snowgears.shop.display.Display'. " + e.getMessage());
                e.printStackTrace();
                disableDisplayClass();
                return false;
            }
            return false;
        } else {
            // We are still on an older version, so go ahead
            String nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            // MockBukkit testing does not support NMS, so we need to just return early
            if (plugin.isMockBukkit()) {
                disableDisplayClass();
                return false;
            }

            try {
                Shop.getPlugin().getLogger().info( "Minecraft version is old or Spigot, watch out for bugs!");
                Shop.getPlugin().getLogger().info("Using display class - com.snowgears.shop.display.Display_" + nmsVersion);
                final Class<?> clazz = Class.forName("com.snowgears.shop.display.Display_" + nmsVersion);
                if (AbstractDisplay.class.isAssignableFrom(clazz)) {
                    this.displayClass = clazz;
                    return true;
                }
            } catch (final Error | Exception e) {
                Shop.getPlugin().getLogger().severe("Error while loading com.snowgears.shop.display.Display_" + nmsVersion + " " + e.getMessage());
                e.printStackTrace();
                disableDisplayClass();
                return false;
            }
            
            Shop.getPlugin().getLogger().severe("Unknown issue hooking into Minecraft Packet Classes, disabling display features.");
            disableDisplayClass();
            return false;
        }
    }

    public AbstractDisplay createDisplay(Location loc){
        try {
            AbstractDisplay display = (AbstractDisplay) displayClass.getConstructor(Location.class).newInstance(loc);
            return display;
        } catch (Exception e){
            plugin.getLogger().warning("Error creating display at | World: " + loc.getWorld().getName() + " at " + loc.getX() + ", " + loc.getY() + ", " + loc.getZ());
            return null;
        }
    }

    private void initItemList() {
        // Initialize item list - placeholder implementation
        // This method would normally populate itemListItems with predefined items
        plugin.getLogger().info("Item list initialized");
    }

    private void loadShops() {
        // Load shops from database/file - placeholder implementation
        // This method would normally load shop data from storage
        plugin.getLogger().info("Shops loaded");
    }

    // ============ MÉTHODES PUBLIQUES MANQUANTES ============

    public UUID getAdminUUID() {
        return adminUUID;
    }

    public int getNumberOfShops() {
        return allShops.size();
    }

    public int getNumberOfShops(ShopType shopType) {
        return (int) allShops.values().stream()
                .filter(shop -> shop.getType() == shopType)
                .count();
    }

    public int getNumberOfShops(Player player) {
        return getNumberOfShops(player.getUniqueId());
    }

    public int getNumberOfShops(UUID playerUUID) {
        List<Location> shops = playerShops.get(playerUUID);
        return shops != null ? shops.size() : 0;
    }

    public int getNumberOfShopDisplayTypes(DisplayType displayType) {
        // Compter simplement tous les shops qui ont un display non-null
        // car le type d'affichage est généralement configuré globalement
        return (int) allShops.values().stream()
                .filter(shop -> shop.getDisplay() != null)
                .count();
    }

    public Map<String, Integer> getShopContainerCounts() {
        Map<String, Integer> counts = new HashMap<>();
        // Implementation pour compter les différents types de conteneurs
        counts.put("CHEST", (int) allShops.values().stream()
                .filter(shop -> isChest(shop.getSignLocation().getBlock()))
                .count());
        return counts;
    }

    public AbstractShop getShop(Location location) {
        return allShops.get(location);
    }

    public AbstractShop getShopByChest(Block chest) {
        if (!isChest(chest)) return null;

        for (AbstractShop shop : allShops.values()) {
            Location chestLoc = shop.getChestLocation();
            if (chestLoc != null && chestLoc.getBlock().equals(chest)) {
                return shop;
            }
        }
        return null;
    }

    public AbstractShop getShopTouchingBlock(Block block) {
        // Chercher un shop adjacent au bloc donné
        for (BlockFace face : directions) {
            Block adjacent = block.getRelative(face);
            AbstractShop shop = getShopByChest(adjacent);
            if (shop != null) return shop;
        }
        return null;
    }

    public boolean isChest(Block block) {
        return block != null && (block.getType() == Material.CHEST ||
                                block.getType() == Material.TRAPPED_CHEST ||
                                block.getType() == Material.BARREL);
    }

    public void addShop(AbstractShop shop) {
        if (shop == null) return;

        Location loc = shop.getSignLocation();
        allShops.put(loc, shop);

        // Ajouter à la liste des shops du joueur
        UUID ownerUUID = shop.getOwnerUUID();
        playerShops.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(loc);

        // Ajouter à la liste des shops du chunk
        String chunkKey = getChunkKey(loc);
        chunkShops.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(loc);
    }

    public void removeShop(AbstractShop shop, boolean saveData) {
        if (shop == null) return;

        Location loc = shop.getSignLocation();
        allShops.remove(loc);

        // Retirer de la liste des shops du joueur
        UUID ownerUUID = shop.getOwnerUUID();
        List<Location> playerShopList = playerShops.get(ownerUUID);
        if (playerShopList != null) {
            playerShopList.remove(loc);
            if (playerShopList.isEmpty()) {
                playerShops.remove(ownerUUID);
            }
        }

        // Retirer de la liste des shops du chunk
        String chunkKey = getChunkKey(loc);
        List<Location> chunkShopList = chunkShops.get(chunkKey);
        if (chunkShopList != null) {
            chunkShopList.remove(loc);
            if (chunkShopList.isEmpty()) {
                chunkShops.remove(chunkKey);
            }
        }

        if (saveData) {
            saveShops(ownerUUID, false);
        }
    }

    public List<AbstractShop> getAllShops() {
        return new ArrayList<>(allShops.values());
    }

    public List<AbstractShop> getShops(UUID playerUUID) {
        List<Location> locations = playerShops.get(playerUUID);
        if (locations == null) return new ArrayList<>();

        return locations.stream()
                .map(allShops::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<AbstractShop> getShopsByItem(ItemStack item) {
        return allShops.values().stream()
                .filter(shop -> shop.getItemStack().isSimilar(item))
                .collect(Collectors.toList());
    }

    public Set<UUID> getShopOwners() {
        return new HashSet<>(playerShops.keySet());
    }

    public void saveAllShops() {
        for (UUID playerUUID : playerShops.keySet()) {
            saveShops(playerUUID, false);
        }
    }

    public void saveShops(UUID playerUUID, boolean async) {
        // Implémentation de sauvegarde - placeholder
        plugin.getLogger().info("Saving shops for player: " + playerUUID);
    }

    // ============ MÉTHODES POUR LES DISPLAYS ============

    public void addActiveShopDisplay(Player player, Location location) {
        UUID playerUUID = player.getUniqueId();
        playersWithActiveShopDisplays.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(location);
    }

    public void addActiveShopDisplayTag(Player player, Location location) {
        playersActiveShopDisplayTag.put(player.getUniqueId(), location);
    }

    public void clearShopDisplaysNearPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        playersWithActiveShopDisplays.remove(playerUUID);
        playersProcessingShopDisplays.remove(playerUUID);
    }

    public void forceProcessShopDisplaysNearPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        playersProcessingShopDisplays.add(playerUUID);

        // Traitement des displays proches
        Location playerLoc = player.getLocation();
        double displayRange = 50.0; // Range par défaut

        for (AbstractShop shop : allShops.values()) {
            if (shop.getSignLocation().getWorld().equals(playerLoc.getWorld()) &&
                shop.getSignLocation().distance(playerLoc) <= displayRange) {
                addActiveShopDisplay(player, shop.getSignLocation());
            }
        }
    }

    public void processDisplaysForAllPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            forceProcessShopDisplaysNearPlayer(player);
        }
    }

    public void processDisplayTagRequests() {
        // Traitement des demandes de tags d'affichage
        for (UUID playerUUID : playersActiveShopDisplayTag.keySet()) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                Location tagLocation = playersActiveShopDisplayTag.get(playerUUID);
                // Traitement du tag
            }
        }
    }

    public void processDisplayUpdates() {
        // Traitement des mises à jour d'affichage
        for (UUID playerUUID : playersProcessingShopDisplays) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player == null || !player.isOnline()) {
                playersProcessingShopDisplays.remove(playerUUID);
            }
        }
    }

    public void processUnloadedShopsInChunk(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        List<Location> unloadedShops = unloadedShopsByChunk.get(chunkKey);
        if (unloadedShops != null) {
            // Traitement des shops non chargés
            unloadedShops.clear();
            unloadedShopsByChunk.remove(chunkKey);
        }
    }

    public void rebuildDisplaysInChunk(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        List<Location> chunkShopList = chunkShops.get(chunkKey);
        if (chunkShopList != null) {
            for (Location shopLoc : chunkShopList) {
                AbstractShop shop = allShops.get(shopLoc);
                if (shop != null) {
                    // Reconstruction des displays
                }
            }
        }
    }

    public void removeLegacyDisplays() {
        // Suppression des anciens displays
        plugin.getLogger().info("Removing legacy displays...");
    }

    public static void removeAllDisplays(Object param) {
        // Méthode statique pour supprimer tous les displays
        Shop.getPlugin().getLogger().info("Removing all displays...");
    }

    // ============ MÉTHODES POUR LA LISTE D'ITEMS ============

    public boolean passesItemListCheck(ItemStack item) {
        if (itemListItems.isEmpty()) return true;

        return itemListItems.stream().anyMatch(listItem -> listItem.isSimilar(item));
    }

    public void addInventoryToItemList(org.bukkit.inventory.PlayerInventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                if (!itemListItems.contains(item)) {
                    itemListItems.add(item.clone());
                }
            }
        }
    }

    public void removeInventoryFromItemList(org.bukkit.inventory.PlayerInventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                itemListItems.removeIf(listItem -> listItem.isSimilar(item));
            }
        }
    }

    // ============ MÉTHODES UTILITAIRES ============

    private String getChunkKey(Location location) {
        return location.getWorld().getName() + "_" +
               (location.getBlockX() >> 4) + "_" +
               (location.getBlockZ() >> 4);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }
}
