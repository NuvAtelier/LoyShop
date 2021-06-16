package com.snowgears.shop.listener;

import com.snowgears.shop.AbstractShop;
import com.snowgears.shop.Shop;
import com.snowgears.shop.display.Display;
import com.snowgears.shop.display.DisplayTagOption;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DisplayListener implements Listener {

    public Shop plugin = Shop.getPlugin();
    private ArrayList<ItemStack> allServerRecipeResults = new ArrayList<>();

    // Create list of viewed shops to remove armor stands on 15 tick loop rather than at expiry if player is no longer viewing sign
    private ArrayList<AbstractShop> viewedShops = new ArrayList<>();

    private HashMap<String, Long> debugAverageChunkTimes = new HashMap<>();
    private HashMap<String, Integer> debugAverageChunkAmounts = new HashMap<>();

    public void startRepeatingDisplayViewTask() {
        if (plugin.displayNameTags() == DisplayTagOption.VIEW_SIGN) {
            //run task every 15 ticks
            Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                ArrayList<AbstractShop> nonViewedShops = new ArrayList<>(viewedShops);
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player != null) {
                        try {
                            Block block = player.getTargetBlock(null, 8);
                            if (block.getBlockData() instanceof WallSign) {
                                AbstractShop shopObj = plugin.getShopHandler().getShop(block.getLocation());
                                if (shopObj != null) {
                                    shopObj.getDisplay().showNameTags();
                                    nonViewedShops.remove(shopObj);
                                    if (!viewedShops.contains(shopObj)) {
                                        viewedShops.add(shopObj);
                                    }
                                }
                            }
                        } catch (IllegalStateException e) {
                            //do nothing, the block iterator missed a block for a player
                        }
                    }
                }
                for (AbstractShop shop : nonViewedShops) {
                    viewedShops.remove(shop);
                    shop.getDisplay().removeTagEntities();
                }
            }, 0, 15);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!entity.isDead()) {
                if (Display.isDisplay(entity)) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        // Other plugins could call this event wrongly, check if the chunk is actually loaded.
        if (chunk.isLoaded()) {
            // In case another plugin loads the chunk asynchronously always make sure to load the holograms on the main thread.
            if (Bukkit.isPrimaryThread()) {
                processChunkLoad(chunk);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> processChunkLoad(chunk));
            }
        }
    }

    private void processChunkLoad(Chunk chunk) {
        List<AbstractShop> shopsInChunk = plugin.getShopHandler().getShopsInChunk(chunk);
        for(AbstractShop shop : shopsInChunk) {
            shop.getDisplay().spawn(false);
        }
        shopsInChunk.clear();
    }

    public void logChunkTime(Chunk chunk, long loadTime, boolean load){
        String key;
        long avg;
        if(load) {
            key = "load_" + chunk.getWorld().getName() + "_"+ chunk.getX() +"_"+ chunk.getZ();
            avg = getAverageChunkLoadTime(chunk);
        }
        else {
            key = "unload_" + chunk.getWorld().getName() + "_"+ chunk.getX() +"_"+ chunk.getZ();
            avg = getAverageChunkUnloadTime(chunk);
        }

        if(avg == -1){
            debugAverageChunkAmounts.put(key, 1);
            debugAverageChunkTimes.put(key, loadTime);
            //System.out.println(key+" - "+loadTime);
        }
        else{
            int amt = debugAverageChunkAmounts.get(key);
            debugAverageChunkAmounts.put(key, amt+1);

            //long newAvg = avg + ((loadTime - avg)/(amt));
            long newAvg = (amt * avg + loadTime) / (amt + 1);
            debugAverageChunkTimes.put(key, newAvg);
            //System.out.println(key+" - "+newAvg);
        }
    }

    public long getAverageChunkLoadTime(Chunk chunk){
        String key = "load_"+chunk.getWorld().getName() + "_"+ chunk.getX() +"_"+ chunk.getZ();
        if(debugAverageChunkTimes.containsKey(key))
            return debugAverageChunkTimes.get(key);
        return -1;
    }

    public long getAverageChunkUnloadTime(Chunk chunk){
        String key = "unload_"+chunk.getWorld().getName() + "_"+ chunk.getX() +"_"+ chunk.getZ();
        if(debugAverageChunkTimes.containsKey(key))
            return debugAverageChunkTimes.get(key);
        return -1;
    }

    public HashMap<String, Long> getDebugAverageChunkTimes(){
        return debugAverageChunkTimes;
    }

    public HashMap<String, Integer> getDebugAverageChunkAmounts(){
        return debugAverageChunkAmounts;
    }

//    @EventHandler
//    public void onChunkLoad(ChunkLoadEvent event){
//        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
//            @Override
//            public void run() {
//                if(event.getChunk().isLoaded()) {
//                    for(AbstractShop shop : plugin.getShopHandler().getShopsInChunk(event.getChunk())){
//                        if(event.getChunk().isLoaded()) {
//                            shop.getDisplay().spawn();
//                        }
//                    }
//                }
//            }
//        }, 20); //1 second later
//    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        if (Display.isDisplay(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandClick(PlayerInteractEntityEvent event) {
        if (Display.isDisplay(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemFrameBreak(HangingBreakEvent event){
        if (Display.isDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    public DisplayListener(Shop instance) {
        plugin = instance;

        new BukkitRunnable() {
            @Override
            public void run() {
                HashMap<ItemStack, Boolean> recipes = new HashMap();
                Iterator<Recipe> recipeIterator = plugin.getServer().recipeIterator();
                while(recipeIterator.hasNext()) {
                    recipes.put(recipeIterator.next().getResult(), true);
                }
                allServerRecipeResults.addAll(recipes.keySet());
                Collections.shuffle(allServerRecipeResults);
            }
        }.runTaskLater(this.plugin, 1); //load all recipes on server once all other plugins are loaded
    }

    public ItemStack getRandomItem(AbstractShop shop){
        try {
            if (shop == null || !plugin.getShopHandler().isChest(shop.getChestLocation().getBlock()))
                return new ItemStack(Material.AIR);
        } catch (NullPointerException e){
            return new ItemStack(Material.AIR);
        }

        if(InventoryUtils.isEmpty(shop.getInventory())) {
            int index = new Random().nextInt(allServerRecipeResults.size());
            //TODO maybe later on add random amount between 1-64 depending on item type
            //like you could get 46 stack of dirt but not 46 stack of swords
            return allServerRecipeResults.get(index);
        } else {
            return InventoryUtils.getRandomItem(shop.getInventory());
        }
    }

    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        AbstractShop shop = plugin.getShopHandler().getShopByChest(event.getToBlock().getRelative(BlockFace.DOWN));
        if (shop != null)
            event.setCancelled(true);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        AbstractShop shop = plugin.getShopHandler().getShopByChest(event.getBlock().getRelative(event.getDirection()).getRelative(BlockFace.DOWN));
        if (shop != null && shop.getDisplay().getType() != DisplayType.NONE)
            event.setCancelled(true);

        for(Block pushedBlock : event.getBlocks()){
            shop = plugin.getShopHandler().getShopByChest(pushedBlock.getRelative(event.getDirection()).getRelative(BlockFace.DOWN));
            if (shop != null && shop.getDisplay().getType() != DisplayType.NONE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (Display.isDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (Display.isDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        AbstractShop shop = plugin.getShopHandler().getShopByChest(event.getBlock().getRelative(BlockFace.DOWN));
        if (shop != null && shop.getDisplay().getType() != DisplayType.NONE)
            event.setCancelled(true);
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onShopInventoryClose(InventoryCloseEvent event) {
        try {
            if(event.getInventory().getHolder() instanceof Container){
                Container container = ((Container)event.getInventory().getHolder());
                AbstractShop shop = plugin.getShopHandler().getShopByChest(container.getBlock());

                if(shop == null)
                    return;

                //refresh display if it's a shulker box (this elevates armor stands)
                if (event.getInventory().getHolder() instanceof ShulkerBox) {
                    if(shop != null){
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if(shop != null)
                                    shop.getDisplay().spawn(true);
                            }
                        }.runTaskLater(this.plugin, 15);
                    }
                }

                //if the sign lines use a variable that requires a refresh (like stock that is dynamically updated), then refresh sign
                if(shop.getSignLinesRequireRefresh())
                    shop.updateSign();

                //set the GUI icon again (in case stock var needs to be updated in the GUI)
                shop.setGuiIcon();
            }
        } catch (NoClassDefFoundError e) {}
    }

    //prevent picking up display items
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if(event.isCancelled())
            return;

        if(Display.isDisplay(event.getItem())){
            event.setCancelled(true);
            //TODO may put this back to remove the entity if its a display not connected to a shop
//            AbstractShop shop = Display.getShop(event.getItem());
//            if(shop != null)
//                shop.getDisplay().spawn(true);
//            else
//                event.getItem().remove();
        }
    }

    //prevent fishing hooks from grabbing display items
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onItemHook(PlayerFishEvent event){
        //if(event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY){
            if(event.getCaught() != null){
                if(Display.isDisplay(event.getCaught())){
                    event.setCancelled(true);
                }
            }
        //}
    }

    //prevent hoppers from grabbing display items
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryPickupItemEvent event) {
        if(Display.isDisplay(event.getItem())){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (Display.isDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
