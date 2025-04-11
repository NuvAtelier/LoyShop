package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.DisplayUtil;
import com.snowgears.shop.util.ItemListType;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;


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

    private ArrayList<UUID> playersSavingShops = new ArrayList<>();

    public ShopHandler(Shop instance) {
        plugin = instance;
        adminUUID = UUID.randomUUID();
        initDisplayClass();
        initItemList();

        plugin.getFoliaLib().getScheduler().runLater(() -> {
            loadShops();
        }, 10);
    }

    private boolean initDisplayClass(){
        String packageName = plugin.getServer().getClass().getPackage().getName();

        // Check if we are on a Paper 1.20.6+ server, or if we are running Spigot v1.20.6 or later :)
        // Now that our new Display class purely uses Class loading to get the appropriate class, we don't
        // need to load a specific revision version class (unless we are old)
        if (packageName.equals("org.bukkit.craftbukkit") || plugin.getNmsBullshitHandler().getServerVersion() >= 120.6D) {
            // We are on a newer version that does not relocate CB classes, load the default display package
            try {
                Shop.getPlugin().getLogger().info("Using item display handler - com.snowgears.shop.display.Display");
                final Class<?> clazz = Class.forName("com.snowgears.shop.display.Display");
                if (AbstractDisplay.class.isAssignableFrom(clazz)) {
                    this.displayClass = clazz;
                    return true;
                }
            } catch (final Exception e) {
                return false;
            }
            return false;
        } else {
            // We are still on an older version, so go ahead
            String nmsVersion = packageName.substring(packageName.lastIndexOf('.') + 1);

            // version did remap even though version number didn't increase
            String mcVersion = Bukkit.getBukkitVersion().substring(0, Bukkit.getBukkitVersion().indexOf('-'));
            //im not doing this right now. I'm only going to support 1.17.1 for now
            //        if (mcVersion.equals("1.17.1")) {
            //            nmsVersion =  "v1_17_R1_2";
            //        }

            try {
                Shop.getPlugin().getLogger().info( "Minecraft version is old or Spigot, watch out for bugs!");
                Shop.getPlugin().getLogger().info("Using display class - com.snowgears.shop.display.Display_" + nmsVersion);
                final Class<?> clazz = Class.forName("com.snowgears.shop.display.Display_" + nmsVersion);
                if (AbstractDisplay.class.isAssignableFrom(clazz)) {
                    this.displayClass = clazz;
                    return true;
                }
            } catch (final Exception e) {
                return false;
            }
            return false;
        }
    }

    public AbstractDisplay createDisplay(Location loc){
        try {
            AbstractDisplay display = (AbstractDisplay) displayClass.getConstructor(Location.class).newInstance(loc);
            return display;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public AbstractShop getShop(Location loc) {
        return allShops.get(loc);
    }

    public AbstractShop getShopByChest(Block shopChest) {

        try {
            if(isChest(shopChest)) {

                AbstractShop shop = null;
                InventoryHolder ih = null;

                //if the shop is a single chest or double chest, add the chest blocks to check
                if (shopChest.getState() instanceof Chest) {
                    Chest chest = (Chest) shopChest.getState();
                    ih = chest.getInventory().getHolder();

                    if (ih instanceof DoubleChest) {

                        DoubleChest dc = (DoubleChest) ih;
                        Chest leftChest = (Chest) dc.getLeftSide();
                        Chest rightChest = (Chest) dc.getRightSide();

                        for (BlockFace direction : directions) {
                            shop = this.getShop(leftChest.getBlock().getRelative(direction).getLocation());
                            if (shop != null) {
                                //make sure the shop sign you found is actually attached to the correct shop
                                if (leftChest.getLocation().equals(shop.getChestLocation()) || rightChest.getLocation().equals(shop.getChestLocation()))
                                    return shop;
                            }
                            shop = this.getShop(rightChest.getBlock().getRelative(direction).getLocation());
                            if (shop != null) {
                                //make sure the shop sign you found is actually attached to the correct shop
                                if (shop.getChestLocation().equals(leftChest.getLocation()) || shop.getChestLocation().equals(rightChest.getLocation()))
                                    return shop;
                            }
                        }
                        return null;
                    }
                }

                for (BlockFace direction : directions) {
                    shop = this.getShop(shopChest.getRelative(direction).getLocation());
                    if (shop != null) {
                        //make sure the shop sign you found is actually attached to the correct shop
                        if (shopChest.getLocation().equals(shop.getChestLocation()))
                            return shop;
                    }
                }
                return null;
            }
        } catch (NoClassDefFoundError e) {}

// TODO not sure what this code was even doing here. might have been leftover

//        if (this.isChest(shopChest)) {
//            //BlockFace chestFacing = UtilMethods.getDirectionOfChest(shopChest);
//
//            ArrayList<Block> chestBlocks = new ArrayList<>();
//            chestBlocks.add(shopChest);
//
//            InventoryHolder ih = null;
//            if(shopChest.getState() instanceof Chest) {
//                Chest chest = (Chest) shopChest.getState();
//                ih = chest.getInventory().getHolder();
//
//                if (ih instanceof DoubleChest) {
//                    DoubleChest dc = (DoubleChest) ih;
//                    Chest leftChest = (Chest) dc.getLeftSide();
//                    Chest rightChest = (Chest) dc.getRightSide();
//                    if (chest.getLocation().equals(leftChest.getLocation()))
//                        chestBlocks.add(rightChest.getBlock());
//                    else
//                        chestBlocks.add(leftChest.getBlock());
//                }
//            }
//
//            for (Block chestBlock : chestBlocks) {
//                Block signBlock = chestBlock.getRelative(chestFacing);
//                if (signBlock.getBlockData() instanceof WallSign) {
//                    WallSign sign = (WallSign) signBlock.getBlockData();
//                    //if (chestFacing == sign.getFacing()) {
//                    AbstractShop shop = this.getShop(signBlock.getLocation());
//                    if (shop != null)
//                        return shop;
//                    //}
//                } else if(!(ih instanceof DoubleChest)){
//                    AbstractShop shop = this.getShop(signBlock.getLocation());
//                    //delete the shop if it doesn't have a sign
//                    if (shop != null)
//                        shop.delete();
//                }
//            }
//        }
        return null;
    }

    public AbstractShop getShopTouchingBlock(Block block){
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for(BlockFace face : faces){
            if(this.isChest(block.getRelative(face))){
                Block shopChest = block.getRelative(face);
                for(BlockFace newFace : faces){
                    if(shopChest.getRelative(newFace).getBlockData() instanceof WallSign){
                        AbstractShop shop = getShop(shopChest.getRelative(newFace).getLocation());
                        if(shop != null)
                            return shop;
                    }
                }
            }
        }
        return null;
    }

    public void addShop(AbstractShop shop) {

        //this is to remove a bug that caused one shop to be saved to multiple files at one point
        AbstractShop s = getShop(shop.getSignLocation());
        if(s != null) {
            return;
        }
        allShops.put(shop.getSignLocation(), shop);

        List<Location> playerShopLocations = getShopLocations(shop.getOwnerUUID());
        if(!playerShopLocations.contains(shop.getSignLocation())) {
            playerShopLocations.add(shop.getSignLocation());
            playerShops.put(shop.getOwnerUUID(), playerShopLocations);
        }

        String chunkKey = getChunkKey(shop.getSignLocation());
        List<Location> chunkShopLocations = getShopLocations(chunkKey);
        //System.out.println("[Shop] 1 - chunkShopLocations "+chunkShopLocations);
        if(!chunkShopLocations.contains(shop.getSignLocation())) {
            chunkShopLocations.add(shop.getSignLocation());
            chunkShops.put(chunkKey, chunkShopLocations);
        }

        plugin.getGuiHandler().reloadPlayerHeadIcon(shop);
    }

    //This method should only be used by AbstractShop object to delete
    public boolean removeShop(AbstractShop shop) {
        if (allShops.containsKey(shop.getSignLocation())) {
            allShops.remove(shop.getSignLocation());
        }
        if(playerShops.containsKey(shop.getOwnerUUID())){
            List<Location> playerShopLocations = getShopLocations(shop.getOwnerUUID());
            if(playerShopLocations.contains(shop.getSignLocation())) {
                playerShopLocations.remove(shop.getSignLocation());
                playerShops.put(shop.getOwnerUUID(), playerShopLocations);
            }
        }
        String chunkKey = getChunkKey(shop.getSignLocation());
        if(chunkShops.containsKey(chunkKey)){
            List<Location> chunkShopLocations = getShopLocations(chunkKey);
            if(chunkShopLocations.contains(shop.getSignLocation())) {
                chunkShopLocations.remove(shop.getSignLocation());
                chunkShops.put(chunkKey, chunkShopLocations);
            }
        }

        return false;
    }

    public void processUnloadedShopsInChunk(Chunk chunk){
        String key = getChunkKey(chunk);
        if(unloadedShopsByChunk.containsKey(key)){
            List<UUID> playerUUIDs = new ArrayList<>();
            List<Location> shopLocations = getUnloadedShopsByChunk(key);
            for(Location shopLocation : shopLocations) {
                AbstractShop shop = getShop(shopLocation);
                if(shop != null){
                    // Run at the shop's location to ensure it works in the correct region in Folia
                    plugin.getFoliaLib().getScheduler().runAtLocation(shopLocation, task -> {
                        boolean signExists = shop.load();
                        if(signExists) {
                            if (!playerUUIDs.contains(shop.getOwnerUUID())) {
                                playerUUIDs.add(shop.getOwnerUUID());
                            }
                        }
                        else{
                            removeShop(shop);
                        }
                    });
                }
            }
            unloadedShopsByChunk.remove(key);
        }
    }

    public void addUnloadedShopToChunkList(AbstractShop shop){
        String chunkKey = getChunkKey(shop.getSignLocation());
        List<Location> shopLocations = getUnloadedShopsByChunk(chunkKey);
        if(!shopLocations.contains(shop.getSignLocation())) {
            shopLocations.add(shop.getSignLocation());
            unloadedShopsByChunk.put(chunkKey, shopLocations);
        }
    }

    public List<AbstractShop> getAllShops(){
        return allShops.values().stream().collect(
                Collectors.toCollection(ArrayList::new)
        );
    }

    public List<AbstractShop> getShops(UUID player){
        List<AbstractShop> shops = new ArrayList<>();
        for(Location shopSign : getShopLocations(player)){
            AbstractShop shop = getShop(shopSign);
            if(shop != null)
                shops.add(shop);
        }
        return shops;
    }

    public int numShopsNeedSave(UUID player){
        List<AbstractShop> shops = getShops(player);

        // Default does not need to be saved;
        int needToBeSaved = 0;
        for (AbstractShop shop : shops) {
            if (shop.needsSave()) { needToBeSaved++; }
        }

        return needToBeSaved;
    }

    public List<AbstractShop> getShopsByItem(ItemStack itemStack){
        List<AbstractShop> shops = new ArrayList<>();
        for(AbstractShop shop : allShops.values()){
            if(shop.getItemStack() != null && shop.getItemStack().getType() == itemStack.getType())
                shops.add(shop);
            else if(shop.getSecondaryItemStack() != null && shop.getSecondaryItemStack().getType() == itemStack.getType())
                shops.add(shop);
        }
        return shops;
    }

    //TODO this is too resource intensive on large servers
    public List<OfflinePlayer> getShopOwners(){
        ArrayList<OfflinePlayer> owners = new ArrayList<>();
        for(UUID player : playerShops.keySet()) {
            owners.add(Bukkit.getOfflinePlayer(player));
        }
        return owners;
    }

    public List<UUID> getShopOwnerUUIDs(){
        ArrayList<UUID> owners = new ArrayList<>();
        for(UUID player : playerShops.keySet()) {
            owners.add(player);
        }
        return owners;
    }

    private List<Location> getShopLocations(UUID player){
        List<Location> shopLocations;
        if(playerShops.containsKey(player)) {
            shopLocations = playerShops.get(player);
        }
        else
            shopLocations = new ArrayList<>();
        return shopLocations;
    }

    private List<Location> getShopLocations(Location locationInChunk){
        String chunkKey = getChunkKey(locationInChunk);
        return getShopLocations(chunkKey);
    }

    private List<Location> getShopLocations(String chunkKey){
        List<Location> shopLocations;
        if(chunkShops.containsKey(chunkKey)) {
            shopLocations = chunkShops.get(chunkKey);
        }
        else {
            shopLocations = new ArrayList<>();
        }
        return shopLocations;
    }

    public HashSet<Location> getShopLocationsNearLocation(Location location){
        int chunkX = UtilMethods.floor(location.getBlockX()) >> 4;
        int chunkZ = UtilMethods.floor(location.getBlockZ()) >> 4;

        HashSet<Location> shopsNearLocation = new HashSet<>();
        String chunkKey;
        for(int x=-1; x<2; x++){
            for(int z=-1; z<2; z++){
                chunkKey = location.getWorld().getName()+"_"+(chunkX+x)+"_"+(chunkZ+z);
                List<Location> shopLocations = getShopLocations(chunkKey);
                shopsNearLocation.addAll(shopLocations);
            }
        }
        return shopsNearLocation;
    }

//    public List<AbstractShop> getShopsNearLocation(Location location){
//        int chunkX = UtilMethods.floor(location.getBlockX()) >> 4;
//        int chunkZ = UtilMethods.floor(location.getBlockZ()) >> 4;
//
//        List<AbstractShop> shopsNearLocation = new ArrayList<>();
//        String chunkKey;
//        for(int x=-1; x<2; x++){
//            for(int z=-1; z<2; z++){
//                chunkKey = location.getWorld().getName()+"_"+(chunkX+x)+"_"+(chunkZ+z);
//                List<Location> shopLocations = getShopLocations(chunkKey);
//                for(Location loc : shopLocations){
//                    shopsNearLocation.add(this.getShop(loc));
//                }
//            }
//        }
//        return shopsNearLocation;
//    }

    public void processShopDisplaysNearPlayer(Player player){
        // Process only one player's shop at a time
        if (playersProcessingShopDisplays.contains(player.getUniqueId())) {
            return;
        }
        playersProcessingShopDisplays.add(player.getUniqueId());

        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            try {
                // Process each shop location near the player
                for (Location shopLocation : getShopLocationsNearLocation(player.getLocation())) {
                    AbstractShop shop = getShop(shopLocation);
                    if (shop == null) continue;

                    // Use distance check to determine if display should be shown
                    double distance = player.getLocation().distance(shop.getSignLocation());
                    if (distance < 2) {
                        // Very close to shop, always show
                        shop.getDisplay().spawn(player);
                        addActiveShopDisplay(player, shop.getSignLocation());
                    } else if (distance < 10) {
                        // Within reasonable distance
                        shop.getDisplay().spawn(player);
                        addActiveShopDisplay(player, shop.getSignLocation());
                    } else {
                        // Too far, remove display
                        shop.getDisplay().remove(player);
                        removeActiveShopDisplay(player, shop.getSignLocation());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing shop displays for player " + player.getName());
                e.printStackTrace();
            } finally {
                playersProcessingShopDisplays.remove(player.getUniqueId());
            }
        }, 0);
    }

    public void clearShopDisplaysNearPlayer(Player player){
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId()))
            playersWithActiveShopDisplays.remove(player.getUniqueId());
    }

    public void addActiveShopDisplay(Player player, Location shopSignLocation){
        HashSet<Location> shops;
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId())){
            shops = playersWithActiveShopDisplays.get(player.getUniqueId());
        }
        else{
            shops = new HashSet<>();
        }
        shops.add(shopSignLocation);
        playersWithActiveShopDisplays.put(player.getUniqueId(), shops);
    }

    public void removeActiveShopDisplay(Player player, Location shopSignLocation){
        HashSet<Location> shops;
        if(playersWithActiveShopDisplays.containsKey(player.getUniqueId())){
            shops = playersWithActiveShopDisplays.get(player.getUniqueId());
            shops.remove(shopSignLocation);
        }
        else{
            shops = new HashSet<>();
        }
        playersWithActiveShopDisplays.put(player.getUniqueId(), shops);
    }

    public void addActiveShopDisplayTag(Player player, Location shopSignLocation){

        if(playersActiveShopDisplayTag.containsKey(player.getUniqueId())){
            Location oldShopSignLocation = playersActiveShopDisplayTag.get(player.getUniqueId());

            if(!oldShopSignLocation.equals(shopSignLocation)) {
                AbstractShop oldShop = getShop(oldShopSignLocation);
                if (oldShop != null && oldShop.getDisplay() != null) {
                    oldShop.getDisplay().removeDisplayEntities(player, true);
                }
            }
        }
        playersActiveShopDisplayTag.put(player.getUniqueId(), shopSignLocation);
    }

//    public boolean shopDisplayTagIsActive(Player player, Location shopSignLocation){
//
//        if(playersActiveShopDisplayTag.containsKey(player.getUniqueId())){
//            Location oldShopSignLocation = playersActiveShopDisplayTag.get(player.getUniqueId());
//            return oldShopSignLocation.equals(shopSignLocation);
//        }
//        return false;
//    }

    private List<Location> getUnloadedShopsByChunk(String chunkKey){
        List<Location> unloadedShopsInChunk;
        if(unloadedShopsByChunk.containsKey(chunkKey)) {
            unloadedShopsInChunk = unloadedShopsByChunk.get(chunkKey);
        }
        else
            unloadedShopsInChunk = new ArrayList<>();
        return unloadedShopsInChunk;
    }

    public int getNumberOfShops() {
        return allShops.size();
    }

    public int getNumberOfShops(Player player) {
        return getShopLocations(player.getUniqueId()).size();
    }

    public int getNumberOfShops(UUID playerUUID) {
        return getShopLocations(playerUUID).size();
    }

    public int getNumberOfShops(ShopType shopType) {
        int shopsWithType = 0;
        for (AbstractShop shop : allShops.values()) {
            if (shop.getType() == shopType) { shopsWithType++; }
        }
        return shopsWithType;
    }

    public int getNumberOfShopDisplayTypes(DisplayType displayType) {
        int shopsWithDisplayType = 0;
        for (AbstractShop shop : allShops.values()) {
            if (shop.getDisplay().getType() == displayType) { shopsWithDisplayType++; }
        }
        return shopsWithDisplayType;
    }

    public Map<String, Integer> getShopContainerCounts() {
        int chestShops = 0;
        int barrelShops = 0;
        int enderChestShops = 0;
        int shulkerBoxShops = 0;
        for (AbstractShop shop : allShops.values()) {
            Material containerType = shop.getContainerType();
            if (containerType == null) continue;
            if (containerType == Material.CHEST || containerType == Material.TRAPPED_CHEST) { chestShops++; }
            if (containerType == Material.BARREL) { barrelShops++; }
            if (containerType == Material.ENDER_CHEST) { enderChestShops++; }
            if (containerType.name().endsWith("SHULKER_BOX")) { shulkerBoxShops++; }
        }
        // Return a map of the container types
        Map<String, Integer> containerTypes = new HashMap<>();
        containerTypes.put("Chest Shops", chestShops);
        containerTypes.put("Barrel Shops", barrelShops);
        containerTypes.put("Ender Chest Shops", enderChestShops);
        containerTypes.put("Shulker Box Shops", shulkerBoxShops);
        return containerTypes;
    }

    private ArrayList<AbstractShop> orderedShopList() {
        ArrayList<AbstractShop> list = new ArrayList<AbstractShop>(allShops.values());
        Collections.sort(list, new Comparator<AbstractShop>() {
            @Override
            public int compare(AbstractShop o1, AbstractShop o2) {
                if(o1 == null || o2 == null)
                    return 0;
                //could have something to do with switching between online and offline mode
                return o1.getOwnerName().toLowerCase().compareTo(o2.getOwnerName().toLowerCase());
            }
        });
        return list;
    }

//    public void refreshShopDisplays(Player player) {
//        for (AbstractShop shop : allShops.values()) {
//            //check that the shop is loaded first
//            if(shop.getChestLocation() != null)
//                shop.getDisplay().spawn(player);
//        }
//    }

    private String getChunkKey(Location location){
        int chunkX = UtilMethods.floor(location.getBlockX()) >> 4;
        int chunkZ = UtilMethods.floor(location.getBlockZ()) >> 4;
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown_world";
        return worldName+"_"+chunkX+"_"+chunkZ;
    }

    private String getChunkKey(Chunk chunk){
        return chunk.getWorld().getName()+"_"+chunk.getX()+"_"+chunk.getZ();
    }

    public void removeAllDisplays(Player player) {
        for (AbstractShop shop : allShops.values()) {
            shop.getDisplay().remove(player);
        }
    }

    public void removeLegacyDisplays(){
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if(DisplayUtil.isDisplay(entity)){
                    entity.remove();
                }
                //make to sure to clear items from old version of plugin too
                else if (entity.getType() == EntityType.ITEM) {
                    ItemMeta itemMeta = ((Item) entity).getItemStack().getItemMeta();
                    if (UtilMethods.stringStartsWithUUID(itemMeta.getDisplayName())) {
                        entity.remove();
                    }
                }
            }
        }
        for(UUID shopOwnerUUID : plugin.getShopHandler().getShopOwnerUUIDs()){
            for(AbstractShop shop : plugin.getShopHandler().getShops(shopOwnerUUID)){
                if(shop.getChestLocation().getChunk().isLoaded()) {
                    plugin.getLogger().debug("[ShopHander.removeLegacyDisplays] updateSign");
                    shop.updateSign();
                }
            }
        }
    }

    public int saveShops(final UUID player){ return saveShops(player, false); }
    public int saveShops(final UUID player, boolean force){
        return saveShopsDriver(player, force);
//        // async code
//        if(playersSavingShops.contains(player))
//            return 0;
//
//        playersSavingShops.add(player);
//        saveShopsDriver(player, force);
//
//        BukkitScheduler scheduler = plugin.getServer().getScheduler();
//        scheduler.runTaskAsynchronously(plugin, new Runnable() {
//            @Override
//            public void run() {
//                playersSavingShops.add(player);
//                saveShopsDriver(player, force);
//            }
//        });
    }

    private int saveShopsDriver(UUID player, boolean force) {
        // Check if any of the players shops want to be saved
        String playerName = player == this.getAdminUUID() ? "admin" : plugin.getServer().getOfflinePlayer(player).getName();
        int numWantingToUpdate = numShopsNeedSave(player);
        if (!force && numWantingToUpdate == 0) {
            plugin.getLogger().trace("save shops for player (" + playerName + ") was called, but no shops for player need updating! " + player.toString());
//            // async code
//            if(playersSavingShops.contains(player)){
//                playersSavingShops.remove(player);
//            }
            return 0;
        }

        // There are shops that need to be saved, so go ahead and save the file!
        plugin.getLogger().debug("attempting to save shops for player " + playerName + " (" + player.toString() + ") isAdmin: " + (player == Shop.getPlugin().getShopHandler().getAdminUUID()));
        File currentFile = null;
        try {

            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            //UtilMethods.deleteDirectory(fileDirectory);
            if (!fileDirectory.exists())
                fileDirectory.mkdir();

            String owner = null;
            if(player.equals(adminUUID)) {
                owner = "admin";
                currentFile = new File(fileDirectory + "/admin.yml");
            }
            else {
                owner = player.toString();
                //currentFile = new File(fileDirectory + "/" + owner + " (" + player.toString() + ").yml");
                currentFile = new File(fileDirectory + "/" + player.toString() + ".yml");
            }
            //owner = currentFile.getName().substring(0, currentFile.getName().length()-4); //remove .yml

            plugin.getLogger().trace("    current file " + currentFile);

            if (!currentFile.exists()) // file doesn't exist
                currentFile.createNewFile();
            else{
                currentFile.delete();
                currentFile.createNewFile();
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(currentFile);
            plugin.getLogger().trace("    loaded yaml... " + currentFile);

            List<AbstractShop> shopList = getShops(player);
            if (shopList.isEmpty()) {
                currentFile.delete();
                plugin.getLogger().debug("    no shops exist for player (" + playerName + "), deleting file... " + currentFile);
//                // async code
//                if(playersSavingShops.contains(player)){
//                    playersSavingShops.remove(player);
//                }
                return 0;
            }

            int shopNumber = 1;
            for (AbstractShop shop : shopList) {

                //this is to remove a bug that caused one shop to be saved to multiple files at one point
                if(!shop.getOwnerUUID().equals(player))
                    continue;

                //don't save shops that are not initialized with items
                if (shop.isInitialized()) {
                    config.set("shops." + owner + "." + shopNumber + ".id", shop.getId().toString());
                    config.set("shops." + owner + "." + shopNumber + ".location", locationToString(shop.getSignLocation()));
                    if(shop.getFacing() != null)
                        config.set("shops." + owner + "." + shopNumber + ".facing", shop.getFacing().toString());
                    config.set("shops." + owner + "." + shopNumber + ".price", shop.getPrice());
                    if(shop.getType() == ShopType.COMBO){
                        config.set("shops." + owner + "." + shopNumber + ".priceSell", ((ComboShop)shop).getPriceSell());
                    }
                    config.set("shops." + owner + "." + shopNumber + ".amount", shop.getAmount());
                    String type = "";
                    if (shop.isAdmin())
                        type = "admin ";
                    type = type + shop.getType().toString();
                    config.set("shops." + owner + "." + shopNumber + ".type", type);
                    if(shop.getDisplay().getType() != null) {
                        config.set("shops." + owner + "." + shopNumber + ".displayType", shop.getDisplay().getType().toString());
                    }
                    else{ //not sure why I have to do this but if I don't it will be set to LARGE_ITEM for some reason (I cannot find right now)
                        config.set("shops." + owner + "." + shopNumber + ".displayType", null);
                    }
                    //only write the variable if true
                    if(shop.isFakeSign()){
                        config.set("shops." + owner + "." + shopNumber + ".fakeSign", shop.isFakeSign());
                    }

                    config.set("shops." + owner + "." + shopNumber + ".stock", shop.getStock());

                    ItemStack itemStack = shop.getItemStack();
                    itemStack.setAmount(1);
                    if(shop.getType() == ShopType.GAMBLE)
                        itemStack = new ItemStack(Material.AIR);
                    config.set("shops." + owner + "." + shopNumber + ".item", itemStack);

                    if (shop.getType() == ShopType.BARTER) {
                        ItemStack barterItemStack = shop.getSecondaryItemStack();
                        barterItemStack.setAmount(1);
                        config.set("shops." + owner + "." + shopNumber + ".itemBarter", barterItemStack);
                    }

                    shop.setNeedsSave(false);
                    shopNumber++;
                }
            }
            plugin.getLogger().spam("    built config to save... \n" + config.saveToString());
            config.save(currentFile);
            plugin.getLogger().helpful("Saved " + shopNumber + " Shops for Player " + playerName + " to file: " + currentFile);
            return shopNumber;
        } catch (Exception e){
            plugin.getLogger().severe("Unable to save player shop file: " + currentFile);
            e.printStackTrace();
            return 0;
        }
    }

    public int saveAllShops() {
        HashMap<UUID, Boolean> allPlayersWithShops = new HashMap<>();
        for (AbstractShop shop : allShops.values()) {
            allPlayersWithShops.put(shop.getOwnerUUID(), true);
        }

        int numberUpdated = 0;
        int playersWithUpdate = 0;
        for (UUID player : allPlayersWithShops.keySet()) {
            int shopsUpdated = saveShops(player);

            if (shopsUpdated > 0) {
                numberUpdated += shopsUpdated;
                playersWithUpdate++;
            }
        }
        if (playersWithUpdate > 0) plugin.getLogger().info("Saved " + playersWithUpdate + " Player Shop file updates to disk");
        return numberUpdated;
    }

    public void convertLegacyShopSaves(){
        //save to new format
        saveAllShops();

        File fileDirectory = new File(plugin.getDataFolder(), "Data");
        if (!fileDirectory.exists())
            return;

        // load all the yml files from the data directory
        for (File file : fileDirectory.listFiles()) {
            if (file.isFile()) {
                if (file.getName().endsWith(".yml")
                        && !file.getName().contains("enderchests")
                        && !file.getName().contains("itemCurrency")
                        && !file.getName().contains("gambleDisplay")) {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    boolean isLegacyConfig = false;
                }
            }
        }
    }

    public void loadShops() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                int numShopsLoaded = 0;

                boolean convertLegacySaves = false;
                File fileDirectory = new File(plugin.getDataFolder(), "Data");
                if (!fileDirectory.exists())
                    return;

                // load all the yml files from the data directory
                for (File file : fileDirectory.listFiles()) {
                    Shop.getPlugin().getLogger().debug("Loading player shops from file: " + file.getName());
                    try {
                        if (file.isFile()) {
                            if (file.getName().endsWith(".yml")
                                    && !file.getName().contains("enderchests")
                                    && !file.getName().contains("itemCurrency")
                                    && !file.getName().contains("gambleDisplay")) {
                                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                                boolean isLegacyConfig = false;
                                UUID playerUUID = null;
                                String fileNameNoExt = null;
                                try {
                                    int dotIndex = file.getName().lastIndexOf('.');
                                    fileNameNoExt = file.getName().substring(0, dotIndex); //remove .yml

                                    //all files are saved as UUID.yml except for admin shops which are admin.yml
                                    if (!fileNameNoExt.equals("admin")) {
                                        playerUUID = UUID.fromString(fileNameNoExt);
                                        //file names are in UUID format. Load from new save files -> ownerUUID.yml
                                    } else {
                                        playerUUID = adminUUID;
                                    }
                                } catch (IllegalArgumentException iae) {
                                    //file names are not in UUID format. Load from legacy save files -> ownerName + " (" + ownerUUID + ").yml
                                    isLegacyConfig = true;
                                    convertLegacySaves = true;
                                    playerUUID = uidFromString(fileNameNoExt);
                                }
                                numShopsLoaded += loadShopsFromConfig(config, isLegacyConfig);
                                if (isLegacyConfig) {
                                    //save new file
                                    saveShops(playerUUID, true);
                                    //delete old file
                                    file.delete();
                                }
                                if (plugin.getDebug_forceResaveAll()) {
                                    saveShops(playerUUID, true);
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        Shop.getPlugin().getLogger().log(Level.SEVERE, "Error while loading Shop from player file (" + file.getName() + "), please report this error to the Shop developers: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                if(convertLegacySaves)
                    convertLegacyShopSaves();

                Shop.getPlugin().getLogger().log(Level.INFO, "Loaded " + numShopsLoaded + " Shops!");

                //dont refresh displays at load time anymore. they are now loaded in client side on login
//                new BukkitRunnable() {
//                    @Override
//                    public void run() {
//                        refreshShopDisplays(null);
//                    }
//                }.runTaskLater(plugin, 20);
            }
        });
    }


    private int loadShopsFromConfig(YamlConfiguration config, boolean isLegacy) {
        if (config.getConfigurationSection("shops") == null)
            return 0;

        int numShopsLoaded = 0;
        Set<String> allShopOwners = config.getConfigurationSection("shops").getKeys(false);

        for (String shopOwner : allShopOwners) {
            UUID owner = null;
            //System.out.println("[Shop] loading shops for player - "+shopOwner);

            Set<String> allShopNumbers = config.getConfigurationSection("shops." + shopOwner).getKeys(false);
            int playerLoadedShops = 0;
            for (String shopNumber : allShopNumbers) {
                Location signLoc = locationFromString(config.getString("shops." + shopOwner + "." + shopNumber + ".location"));
                if(signLoc != null) {
                    try {
                        // Track each shop using a uuid so that we can log purchases better
                        String idString = config.getString("shops." + shopOwner + "." + shopNumber + ".id");
                        UUID id = null;
                        if (idString != null && !idString.isEmpty()){
                            id = UUID.fromString(idString);
                        }

                        if (shopOwner.equals("admin"))
                            owner = this.getAdminUUID();
                        else if(isLegacy)
                            owner = uidFromString(shopOwner);
                        else
                            owner = UUID.fromString(shopOwner);

                        BlockFace facing = null;
                        String facingStr = config.getString("shops." + shopOwner + "." + shopNumber + ".facing");
                        if(facingStr != null)
                            facing = BlockFace.valueOf(facingStr);

                        String type = config.getString("shops." + shopOwner + "." + shopNumber + ".type");
                        double price = Double.parseDouble(config.getString("shops." + shopOwner + "." + shopNumber + ".price"));
                        double priceSell = 0;
                        if (config.getString("shops." + shopOwner + "." + shopNumber + ".priceSell") != null) {
                            priceSell = Double.parseDouble(config.getString("shops." + shopOwner + "." + shopNumber + ".priceSell"));
                        }
                        int amount = Integer.parseInt(config.getString("shops." + shopOwner + "." + shopNumber + ".amount"));

                        boolean isAdmin = shopOwner.equals("admin");

                        ShopType shopType = typeFromString(type);

                        ItemStack itemStack = config.getItemStack("shops." + shopOwner + "." + shopNumber + ".item");
                        if (shopType == ShopType.GAMBLE) {
                            itemStack = plugin.getGambleDisplayItem();
                        }

                        if (itemStack == null) {
                            Shop.getPlugin().getLogger().log(Level.WARNING, "Unable to load Shop #" + shopNumber + " for owner '" + shopOwner + "'! Skipping!");
                            continue;
                        }

                        //this inits a new shop but wont calculate anything yet
                        AbstractShop shop = AbstractShop.create(signLoc, owner, price, priceSell, amount, isAdmin, shopType, facing);
                        if (id != null) shop.setId(id);
                        shop.setItemStack(itemStack);
                        if (shop.getType() == ShopType.BARTER) {
                            ItemStack barterItemStack = config.getItemStack("shops." + shopOwner + "." + shopNumber + ".itemBarter");
                            shop.setSecondaryItemStack(barterItemStack);
                        }
                        String displayType = config.getString("shops." + shopOwner + "." + shopNumber + ".displayType");
                        if(displayType != null)
                            shop.getDisplay().setType(DisplayType.valueOf(displayType), false);

                        boolean isFakeSign = config.getBoolean("shops." + shopOwner + "." + shopNumber + ".fakeSign");
                        if(isFakeSign){
                            shop.setFakeSign(true);
                        }

                        int stock = config.getInt("shops." + shopOwner + "." + shopNumber + ".stock");
                        shop.setStockOnLoad(stock);
                        // Load the GUI Icon so that it appears when players perform a search, even if the chunks haven't loaded yet.
                        shop.refreshGuiIcon();

                        // We will need to save the file again if we are a legacy config
                        if (isLegacy) { shop.setNeedsSave(true); }
                        // If we just added an ID to a shop for the first time, then it will need to be saved/updated as well
                        if (idString == null || idString.isEmpty()) { shop.setNeedsSave(true); }

                        //if chunk its in is already loaded, calculate it here
                        if(shop.getDisplay().isChunkLoaded()) {
                            //run this task synchronously
                            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    boolean signDefined = shop.load();
                                    if(signDefined)
                                        addShop(shop);
                                }
                            });
                        }
                        //if the chunk is not already loaded, add it to a list to calculate it at chunkloadevent later
                        else {
                            //System.out.println("[Shop] chunk not loaded. Adding to unloadedList");
                            addUnloadedShopToChunkList(shop);
                            addShop(shop);
                        }

                        numShopsLoaded++;
                        playerLoadedShops++;
                    } catch (NullPointerException e) {e.printStackTrace();}
                }
            }
            String ownerName = shopOwner.equals("admin") ? "admin" : plugin.getServer().getOfflinePlayer(UUID.fromString(shopOwner)).getName();
            plugin.getLogger().helpful("Loaded (" + playerLoadedShops + ") shops for Player " + ownerName + " from: " + shopOwner + ".yml");
        }

        return numShopsLoaded;
    }

    public UUID getAdminUUID(){
        return adminUUID;
    }


    private String locationToString(Location loc) {
        String worldName = loc.getWorld() == null ? "unknown_world" : loc.getWorld().getName();
        return worldName + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location locationFromString(String locString) {
        String[] parts = locString.split(",");
        return new Location(plugin.getServer().getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    }

    private UUID uidFromString(String ownerString) {
        int index = ownerString.indexOf("(");
        String uidString = ownerString.substring(index + 1, ownerString.length() - 1);
        return UUID.fromString(uidString);
    }

    private ShopType typeFromString(String typeString) {
        if (typeString.contains("sell"))
            return ShopType.SELL;
        else if (typeString.contains("buy"))
            return ShopType.BUY;
        else if(typeString.contains("barter"))
            return ShopType.BARTER;
        else if(typeString.contains("combo"))
            return ShopType.COMBO;
        else
            return ShopType.GAMBLE;
    }

    public boolean isChest(Block b){
        return plugin.getEnabledContainers().contains(b.getType());
    }

    public boolean passesItemListCheck(ItemStack is){
        if(plugin.getItemListType() == ItemListType.NONE)
            return true;

        for(ItemStack itemInList : itemListItems){
            if(itemInList.isSimilar(is)){
                if(plugin.getItemListType() == ItemListType.ALLOW_LIST)
                    return true;
                else if(plugin.getItemListType() == ItemListType.DENY_LIST)
                    return false;
            }
        }

        //item not similar to anything in our item list
        if(plugin.getItemListType() == ItemListType.ALLOW_LIST)
            return false;

        return true;
    }

    public void addInventoryToItemList(Inventory inventory){
        for(ItemStack is : inventory.getContents()) {
            if(is != null && is.getType() != Material.AIR) {
                ItemStack itemClone = is.clone();
                itemClone.setAmount(1);
                boolean doNotAdd = false;
                for (ItemStack itemInList : itemListItems) {
                    if (itemInList.isSimilar(itemClone)) {
                        doNotAdd = true;
                    }
                }
                if(!doNotAdd){
                    itemListItems.add(itemClone);
                }
            }
        }
        saveItemList();
    }

    public void removeInventoryFromItemList(Inventory inventory){
        Iterator<ItemStack> itemIterator = itemListItems.iterator();
        while(itemIterator.hasNext()){
            ItemStack listItem = itemIterator.next();
            for(ItemStack toRemove : inventory.getContents()) {
                if(toRemove != null && toRemove.getType() != Material.AIR) {
                    ItemStack itemClone = toRemove.clone();
                    itemClone.setAmount(1);
                    if (listItem.isSimilar(itemClone)) {
                        itemIterator.remove();
                    }
                }
            }
        }
        saveItemList();
    }

    public void initItemList(){
        if(plugin.getItemListType() == ItemListType.NONE)
            return;

        try {
            File itemListFile = new File(plugin.getDataFolder() + "/itemList.yml");

            if (!itemListFile.exists()) { // file doesn't exist{
                itemListFile.createNewFile();
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(itemListFile);
            for(String key : config.getKeys(false)){
                ItemStack is = config.getItemStack(key);
                if(is != null) {
                    is.setAmount(1);
                    itemListItems.add(is);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveItemList(){
        if(plugin.getItemListType() == ItemListType.NONE)
            return;

        try {
            File itemListFile = new File(plugin.getDataFolder() + "/itemList.yml");

            if (!itemListFile.exists()) { // file doesn't exist{
                itemListFile.createNewFile();
            }
            else{
                itemListFile.delete();
                itemListFile.createNewFile();
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(itemListFile);
            for(int i=0; i< itemListItems.size(); i++){
                ItemStack is = itemListItems.get(i);
                if(is != null) {
                    config.set(""+i, is);
                }
            }

            config.save(itemListFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}