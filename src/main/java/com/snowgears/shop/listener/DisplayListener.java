package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayTagOption;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.GambleShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class DisplayListener implements Listener {

    public Shop plugin;
    private ArrayList<ItemStack> allServerRecipeResults = new ArrayList<>();
    private BukkitTask repeatingViewTask;
    private BukkitTask repeatingDisplayTask;

    public void startRepeatingDisplayViewTask() {
        if (plugin.getDisplayTagOption() == DisplayTagOption.VIEW_SIGN) {
            repeatingViewTask = new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getShopHandler().processDisplayTagRequests();
                }
            }.runTaskTimer(plugin, 0, 20);
        }
    }

    public void startRepeatingDisplayTask() {
        repeatingDisplayTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getShopHandler().processDisplaysForAllPlayers();

                // Additional processing that needs to run on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getShopHandler().processDisplayUpdates();
                });
            }
        }.runTaskTimerAsynchronously(plugin, 0, (int) (plugin.getDisplayProcessInterval() * 20));
    }

    public DisplayListener(Shop instance) {
        plugin = instance;

        // Load all recipes on server once all other plugins are loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HashMap<ItemStack, Boolean> recipes = new HashMap();
            Iterator<Recipe> recipeIterator = plugin.getServer().recipeIterator();
            while(recipeIterator.hasNext()) {
                ItemStack result = recipeIterator.next().getResult();
                if(result.getAmount() != 0)
                    recipes.put(result, true);
            }
            allServerRecipeResults.addAll(recipes.keySet());
            Collections.shuffle(allServerRecipeResults);
        }, 1);
    }

    public ItemStack getRandomItem(AbstractShop shop){
        if (shop == null)
            return new ItemStack(Material.AIR);


        if(InventoryUtils.isEmpty(shop.getInventory())) {
            int index = new Random().nextInt(allServerRecipeResults.size());
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

        shop = plugin.getShopHandler().getShopByChest(event.getBlock().getRelative(event.getDirection()).getRelative(BlockFace.UP));
        if (shop != null)
            event.setCancelled(true);

        for(Block pushedBlock : event.getBlocks()){
            shop = plugin.getShopHandler().getShopByChest(pushedBlock.getRelative(event.getDirection()).getRelative(BlockFace.DOWN));
            if (shop != null && shop.getDisplay().getType() != DisplayType.NONE) {
                event.setCancelled(true);
                return;
            }

            shop = plugin.getShopHandler().getShopByChest(pushedBlock.getRelative(event.getDirection()).getRelative(BlockFace.UP));
            if (shop != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block pulledBlock = event.getBlock().getRelative(event.getDirection().getOppositeFace()).getRelative(event.getDirection().getOppositeFace()).getRelative(BlockFace.UP);
        AbstractShop shop = plugin.getShopHandler().getShopByChest(pulledBlock);
        if (shop != null)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        AbstractShop shop = plugin.getShopHandler().getShopByChest(event.getBlock().getRelative(BlockFace.DOWN));
        if(shop != null){
            if(shop.getDisplay().getType() == null && plugin.getDisplayType() != DisplayType.NONE)
                event.setCancelled(true);
            else if (shop.getDisplay().getType() != null && shop.getDisplay().getType() != DisplayType.NONE)
                event.setCancelled(true);
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onShopInventoryClose(InventoryCloseEvent event) {
        try {
            if(event.getInventory().getHolder() instanceof Container){
                Container container = ((Container)event.getInventory().getHolder());
                AbstractShop shop = plugin.getShopHandler().getShopByChest(container.getBlock());

                if(shop == null) {
                    return;
                }

                shop.updateStock();

                //make sure to set gamble item again if player set it to new custom items
                if(shop.getType() == ShopType.GAMBLE){
                    ((GambleShop)shop).setGambleItem();
                }
            }
            //for some reason, DoubleChest does not extend Container like Chest does
            else if(event.getInventory().getHolder() instanceof DoubleChest){
                DoubleChest doubleChest = ((DoubleChest)event.getInventory().getHolder());
                AbstractShop shop = plugin.getShopHandler().getShopByChest(doubleChest.getLocation().getBlock());

                if(shop == null) {
                    return;
                }

                shop.updateStock();

                //make sure to set gamble item again if player set it to new custom items
                if(shop.getType() == ShopType.GAMBLE){
                    ((GambleShop)shop).setGambleItem();
                }
            }
        } catch (NoClassDefFoundError e) {}
    }

    public void cancelRepeatingViewTask(){
        if (repeatingViewTask != null) {
            repeatingViewTask.cancel();
        }
        if (repeatingDisplayTask != null) {
            repeatingDisplayTask.cancel();
        }
    }
}
