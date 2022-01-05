package com.snowgears.shop.listener;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.event.PlayerDestroyShopEvent;
import com.snowgears.shop.event.PlayerInitializeShopEvent;
import com.snowgears.shop.event.PlayerResizeShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.*;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;


public class MiscListener implements Listener {

    public Shop plugin;
    private HashMap<UUID, ShopCreationProcess> playerChatCreationSteps = new HashMap<>();

    public MiscListener(Shop instance) {
        plugin = instance;
    }

    //prevent emptying of bucket when player clicks on shop sign
    //also prevent when emptying on display item itself
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block b = event.getBlockClicked();

        if (b.getBlockData() instanceof WallSign) {
            AbstractShop shop = plugin.getShopHandler().getShop(b.getLocation());
            if (shop != null)
                event.setCancelled(true);
        }
        Block blockToFill = event.getBlockClicked().getRelative(event.getBlockFace());
        AbstractShop shop = plugin.getShopHandler().getShopByChest(blockToFill.getRelative(BlockFace.DOWN));
        if (shop != null)
            event.setCancelled(true);
    }

    //player places a sign on a chest and creates an initial shop with no item
    //this method calls PlayerCreateShopEvent
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopCreation(SignChangeEvent event) {
        final Block b = event.getBlock();
        final Player player = event.getPlayer();

        if(!plugin.getAllowCreationMethodSign())
            return;

        if(!(b.getState() instanceof Sign))
            return;

        BlockFace signDirection = null;
        Block chest = null;
        if(b.getBlockData() instanceof WallSign) {
            signDirection = ((WallSign) b.getBlockData()).getFacing();
            chest = b.getRelative(signDirection.getOppositeFace());
        }
        else if(b.getBlockData() instanceof Rotatable){ //regular sign post
            signDirection = ((Rotatable) b.getBlockData()).getRotation();
            //adjust the sign direction to cordinal direction if its not already one
            if( signDirection.toString().indexOf('_') != -1) {
                String adjustedDirString = signDirection.toString().substring(0, signDirection.toString().indexOf('_'));
                signDirection = BlockFace.valueOf(adjustedDirString);
            }
            chest = b.getRelative(signDirection.getOppositeFace());
        }
        else
            return;

        int amount = 0 ;
        ShopType type = null;
        boolean isAdmin = false;
        if (plugin.getShopHandler().isChest(chest)) {
            //System.out.println("Chest can be a shop chest.");
            final Sign signBlock = (Sign) b.getState();
            if (event.getLine(0).toLowerCase().contains(ShopMessage.getCreationWord("SHOP").toLowerCase())) {

                if(!ShopCreationUtil.shopCanBeCreated(player, chest)){
                    event.setCancelled(true);
                    return;
                }

                try {
                    String line2 = UtilMethods.cleanNumberText(event.getLine(1));
                    amount = Integer.parseInt(line2);
                    if (amount < 1) {
                        player.sendMessage(ShopMessage.getMessage("interactionIssue", "line2", null, player));
                        return;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ShopMessage.getMessage("interactionIssue", "line2", null, player));
                    return;
                }

                //change default shop type based on permissions
                //TODO I dont like this. I would rather throw an error
//                type = ShopType.SELL;
//                if (plugin.usePerms()) {
//                    if (!player.hasPermission("shop.create.sell")) {
//                        type = ShopType.BUY;
//                        if (!player.hasPermission("shop.create.buy"))
//                            type = ShopType.BARTER;
//                    }
//                }

                type = ShopCreationUtil.getShopType(event.getLine(3));
                isAdmin = ShopCreationUtil.getShopIsAdmin(event.getLine(3));

                PricePair pricePair = ShopCreationUtil.getShopPricePair(player, event.getLine(2), type);
                if(pricePair == null){
                    event.setCancelled(true);
                    return;
                }

                AbstractShop shop = ShopCreationUtil.createShop(player, chest, signBlock.getBlock(), pricePair, amount, isAdmin, type, signDirection);

                if(shop == null) {
                    event.setCancelled(true);
                    return;
                }

                //give player a limited amount of time to finish creating the shop until it is deleted
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    public void run() {
                        //the shop has still not been initialized with an item from a player
                        if (!shop.isInitialized()) {
                            plugin.getShopHandler().removeShop(shop);
                            if (b.getBlockData() instanceof WallSign) {
                                String[] lines = ShopMessage.getTimeoutSignLines(shop);
                                Sign sign = (Sign) b.getState();
                                sign.setLine(0, lines[0]);
                                sign.setLine(1, lines[1]);
                                sign.setLine(2, lines[2]);
                                sign.setLine(3, lines[3]);
                                sign.update(true);
                                plugin.getCreativeSelectionListener().removePlayerData(player);
                            }
                        }
                    }
                }, 1200L); //1 minute
            }
        }
    }

    //this method calls PlayerInitializeShopEvent
    @EventHandler
    public void onPreShopSignClick(PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;
        }
        try {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                return; // off hand version, ignore.
            }
        } catch (NoSuchMethodError error) {}
        final Player player = event.getPlayer();



        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            final Block clicked = event.getClickedBlock();

            if (clicked.getBlockData() instanceof WallSign) {

                if(!plugin.getAllowCreationMethodSign())
                    return;

                player.sendMessage("[Shop] clicked a wall sign."); //TODO delete this

                //TODO this has a ton of methods for removing money for creation cost and a few other things. Move all to ShopCreationUtil

                AbstractShop shop = plugin.getShopHandler().getShop(clicked.getLocation());
                if (shop == null) {
                    return;
                } else if (shop.isInitialized()) {
                    return;
                }
                if (!player.getUniqueId().equals(shop.getOwnerUUID())) {
                    //do not allow non operators to initialize other player's shops
                    if((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("shop.operator"))) {
                        String message = ShopMessage.getMessage("interactionIssue", "initialize", null, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        plugin.getTransactionListener().sendEffects(false, player, shop);
                        event.setCancelled(true);
                        return;
                    }
                }

                if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                    return;
                }

                if(Shop.getPlugin().getDisplayType() != DisplayType.NONE) {
                    //make sure there is room above the shop for the display
                    Block aboveShop = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
                    if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                        if(plugin.forceDisplayToNoneIfBlocked()){
                            shop.getDisplay().setType(DisplayType.NONE, false);
                            shop.getDisplay().spawn(player);
                            shop.updateSign();
                        }
                        else {
                            String message = ShopMessage.getMessage("interactionIssue", "displayRoom", null, player);
                            if(message != null && !message.isEmpty())
                                player.sendMessage(message);
                            plugin.getTransactionListener().sendEffects(false, player, shop);
                            event.setCancelled(true);
                            return;
                        }
                    }
                }

                //if players must pay to create shops, remove money first
                double cost = plugin.getCreationCost();
                if(cost > 0 && !shop.isAdmin()){
                    boolean removed = EconomyUtils.removeFunds(player, player.getInventory(), cost);
                    if(!removed){
                        String message = ShopMessage.getMessage("interactionIssue", "createInsufficientFunds", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        plugin.getTransactionListener().sendEffects(false, player, shop);
                        event.setCancelled(true);
                        return;
                    }
                }

                ItemStack shopItem = player.getInventory().getItemInMainHand();

                try {
                    //stop the edge case of shulker boxes being able to be used in shulker chests
                    if (Tag.SHULKER_BOXES.isTagged(shopItem.getType())) {
                        if (shop.getChestLocation().getBlock().getState() instanceof ShulkerBox) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                } catch (NoSuchFieldError e) {}

                //if the item is on the DENY LIST or the item is not on the ALLOW LIST, don't let player initialize with it
                if(!(player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator")))) {
                    boolean passesItemList = plugin.getShopHandler().passesItemListCheck(shopItem);
                    if(!passesItemList){
                        String message = ShopMessage.getMessage("interactionIssue", "itemListDeny", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        plugin.getTransactionListener().sendEffects(false, player, shop);
                        event.setCancelled(true);
                        return;
                    }
                }

                if (shop.getItemStack() == null) {

                    PlayerInitializeShopEvent e = new PlayerInitializeShopEvent(player, shop);
                    Bukkit.getServer().getPluginManager().callEvent(e);

                    if(e.isCancelled())
                        return;

                    if(shop.getItemStack() == null)
                        shop.setItemStack(shopItem);
                    if (shop.getType() == ShopType.BARTER) {
                        String message = ShopMessage.getMessage(shop.getType().toString(), "initializeInfo", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        message = ShopMessage.getMessage(shop.getType().toString(), "initializeBarter", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        if(plugin.allowCreativeSelection()) {
                            message = ShopMessage.getMessage("BUY", "initializeAlt", shop, player);
                            if(message != null && !message.isEmpty())
                                player.sendMessage(message);
                        }
                    }
                    else {
                        shop.getDisplay().spawn(player);
                        shop.updateSign();
                        String message = ShopMessage.getMessage(shop.getType().toString(), "create", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        plugin.getTransactionListener().sendEffects(true, player, shop);
                        plugin.getShopHandler().saveShops(shop.getOwnerUUID());
                    }
                } else if (shop.getSecondaryItemStack() == null) {
                    if (!(InventoryUtils.itemstacksAreSimilar(shop.getItemStack(), shopItem))) {

                        PlayerInitializeShopEvent e = new PlayerInitializeShopEvent(player, shop);
                        Bukkit.getServer().getPluginManager().callEvent(e);

                        if(e.isCancelled())
                            return;

                        if(shop.getSecondaryItemStack() == null)
                            shop.setSecondaryItemStack(shopItem);
                        shop.getDisplay().spawn(player);
                        shop.updateSign();
                        String message = ShopMessage.getMessage(shop.getType().toString(), "create", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        plugin.getTransactionListener().sendEffects(true, player, shop);
                        plugin.getShopHandler().saveShops(shop.getOwnerUUID());
                    } else {
                        String message = ShopMessage.getMessage("interactionIssue", "sameItem", null, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        plugin.getTransactionListener().sendEffects(false, player, shop);
                        event.setCancelled(true);
                        return;
                    }
                }
                event.setCancelled(true);
            }
            else if(plugin.getShopHandler().isChest(clicked)){
                if(!plugin.getAllowCreationMethodChest())
                    return;

                if(!ShopCreationUtil.shopCanBeCreated(player, clicked))
                    return;

                //TODO also protect the chest if its in the middle of a chat creation process

                //TODO check that there is room for the sign on the clicked face

                ShopCreationProcess currentProcess = playerChatCreationSteps.get(player.getUniqueId());
                if(currentProcess != null && currentProcess.getStep() == ShopCreationProcess.ChatCreationStep.BARTER_CHEST_HIT){
                    currentProcess.setBarterItemStack(event.getItem());

                    String message = ShopMessage.getUnformattedMessage(currentProcess.getShopType().toString(), "createHitChestBarterAmount");
                    message = ShopMessage.formatMessage(message, currentProcess, player);
                    if(message != null && !message.isEmpty())
                        player.sendMessage(message);
                    event.setCancelled(true);
                    return;
                }

                if(!player.isSneaking())
                    return;

                event.setCancelled(true); //TODO want to cancel this or do I need to cancel block break also?

                ShopCreationProcess process = new ShopCreationProcess(player, clicked, event.getBlockFace());
                process.setItemStack(event.getItem());
                playerChatCreationSteps.put(player.getUniqueId(), process);

                //send player text prompts after they have clicked the chest with the item they want to create a shop with
                String message = ShopMessage.getUnformattedMessage("createHitChest", null);
                message = ShopMessage.formatMessage(message, process, player);
                if(message != null && !message.isEmpty()) {
                    player.sendMessage(message);
                }
                if((!plugin.usePerms() && player.isOp()) || (plugin.usePerms() && player.hasPermission("shop.operator"))) {
                    String adminMessage = ShopMessage.getUnformattedMessage("adminCreateHitChest", null);
                    adminMessage = ShopMessage.formatMessage(adminMessage, process, player);
                    if (adminMessage != null && !adminMessage.isEmpty())
                        player.sendMessage(adminMessage);
                }

                final UUID originalProcessUUID = process.getUniqueID();
                //give player a limited amount of time to finish creating the shop until it is deleted
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    public void run() {
                        //the shop has still not been initialized with an item from a player
                        ShopCreationProcess process = playerChatCreationSteps.get(player.getUniqueId());
                        if (process != null && process.getUniqueID().equals(originalProcessUUID)) {
                            playerChatCreationSteps.remove(player.getUniqueId());
                            plugin.getCreativeSelectionListener().removePlayerData(player);

                            String message = ShopMessage.getUnformattedMessage("interactionIssue", "createHitChestTimeout");
                            message = ShopMessage.formatMessage(message, process, player);
                            if(message != null && !message.isEmpty())
                                event.getPlayer().sendMessage(message);
                        }
                    }
                }, 1200); //1 minute
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event){
        Player player = event.getPlayer();
        if(playerChatCreationSteps.containsKey(player.getUniqueId())){
            ShopCreationProcess process = playerChatCreationSteps.get(player.getUniqueId());
            switch (process.getStep()){
                case SHOP_TYPE:
                    ShopType type = ShopCreationUtil.getShopType(event.getMessage());
                    boolean isAdmin = ShopCreationUtil.getShopIsAdmin(event.getMessage());
                    process.setShopType(type);
                    process.setAdmin(isAdmin);
                    event.setCancelled(true);

                    String message = ShopMessage.getUnformattedMessage(type.toString(), "createHitChestAmount");
                    message = ShopMessage.formatMessage(message, process, player);
                    if(message != null && !message.isEmpty())
                        player.sendMessage(message);

                    break;
                case ITEM_AMOUNT:
                    int amount = 0;
                    try {
                        String textAmt = UtilMethods.cleanNumberText(event.getMessage());
                        amount = Integer.parseInt(textAmt);
                        if (amount < 1) {
                            player.sendMessage(ShopMessage.getMessage("interactionIssue", "line2", null, player));
                            event.setCancelled(true);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ShopMessage.getMessage("interactionIssue", "line2", null, player));
                        event.setCancelled(true);
                        return;
                    }
                    process.setItemAmount(amount);
                    event.setCancelled(true);

                    if(process.getShopType() == ShopType.BARTER){
                        message = ShopMessage.getUnformattedMessage(process.getShopType().toString(), "createHitChest");
                        message = ShopMessage.formatMessage(message, process, player);
                        if (message != null && !message.isEmpty())
                            event.getPlayer().sendMessage(message);
                    }
                    else {
                        message = ShopMessage.getUnformattedMessage(process.getShopType().toString(), "createHitChestPrice");
                        message = ShopMessage.formatMessage(message, process, player);
                        if (message != null && !message.isEmpty())
                            event.getPlayer().sendMessage(message);
                    }
                    break;
                case ITEM_PRICE:
                    double price = ShopCreationUtil.getShopPrice(player, event.getMessage(), process.getShopType());
                    if(price == -1){
                        event.setCancelled(true);
                        return;
                    }
                    process.setPrice(price);
                    event.setCancelled(true);

                    if(process.getStep() == ShopCreationProcess.ChatCreationStep.FINISHED){
                        process.createShop(player);
                        playerChatCreationSteps.remove(player.getUniqueId());
                    }
                    else if(process.getStep() == ShopCreationProcess.ChatCreationStep.ITEM_PRICE_COMBO){
                        message = ShopMessage.getUnformattedMessage(process.getShopType().toString(), "createHitChestPriceCombo");
                        message = ShopMessage.formatMessage(message, process, player);
                        if(message != null && !message.isEmpty())
                            event.getPlayer().sendMessage(message);
                    }
                    break;
                case ITEM_PRICE_COMBO:
                    double priceCombo = ShopCreationUtil.getShopPriceCombo(player, event.getMessage(), process.getShopType());
                    if(priceCombo == -1){
                        event.setCancelled(true);
                        return;
                    }
                    process.setPriceCombo(priceCombo);
                    event.setCancelled(true);

                    if(process.getStep() == ShopCreationProcess.ChatCreationStep.FINISHED){
                        process.createShop(player);
                        playerChatCreationSteps.remove(player.getUniqueId());
                    }
                    break;
                case BARTER_ITEM_AMOUNT:
                    int barterAmount = 0;
                    try {
                        String textAmt = UtilMethods.cleanNumberText(event.getMessage());
                        barterAmount = Integer.parseInt(textAmt);
                        if (barterAmount < 1) {
                            player.sendMessage(ShopMessage.getMessage("interactionIssue", "line2", null, player));
                            event.setCancelled(true);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ShopMessage.getMessage("interactionIssue", "line2", null, player));
                        event.setCancelled(true);
                        return;
                    }
                    process.setBarterItemAmount(barterAmount);
                    event.setCancelled(true);

                    if(process.getStep() == ShopCreationProcess.ChatCreationStep.FINISHED) {
                        process.createShop(player);
                        playerChatCreationSteps.remove(player.getUniqueId());
                    }
                    break;
                default:
                    event.setCancelled(true); //TODO maybe remove this
                    break;
            }
        }
    }

    //player destroys shop, call PlayerDestroyShopEvent or PlayerResizeShopEvent
    @EventHandler(priority = EventPriority.HIGH)
    public void shopDestroy(BlockBreakEvent event) {
        //refresh the shop display even if the event was cancelled as sometimes items can bug out and fall through chest
        if (event.isCancelled()) {
            Block b = event.getBlock();
            if (b.getBlockData() instanceof WallSign) {
                AbstractShop shop = plugin.getShopHandler().getShop(b.getLocation());
                if (shop != null) {
                    if (shop.getDisplay().getType() == DisplayType.ITEM) {
                        shop.getDisplay().spawn(event.getPlayer());
                        shop.updateSign();
                    }
                }
            }
        }

        Block b = event.getBlock();
        Player player = event.getPlayer();

        if (b.getBlockData() instanceof WallSign) {
            AbstractShop shop = plugin.getShopHandler().getShop(b.getLocation());
            if (shop == null)
                return;
            else if (!shop.isInitialized()) {
                event.setCancelled(true);
                return;
            }
            //player trying to break their own shop
            if (shop.getOwnerName().equals(player.getName())) {
                if (plugin.usePerms() && !(player.hasPermission("shop.destroy") || player.hasPermission("shop.operator"))) {
                    event.setCancelled(true);
                    String message = ShopMessage.getMessage("permission", "destroy", shop, player);
                    if(message != null && !message.isEmpty())
                        player.sendMessage(message);
                    return;
                }

                //if players must pay to create shops, remove money first
                double cost = plugin.getDestructionCost();
                if(cost > 0){
                    boolean removed = EconomyUtils.removeFunds(player, player.getInventory(), cost);
                    if(!removed){
                        String message = ShopMessage.getMessage("interactionIssue", "destroyInsufficientFunds", shop, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        return;
                    }
                }

                PlayerDestroyShopEvent e = new PlayerDestroyShopEvent(player, shop);
                plugin.getServer().getPluginManager().callEvent(e);
                if (e.isCancelled()) {
                    event.setCancelled(true);
                    return;
                }
                else{
                    if((!shop.isAdmin()) && plugin.returnCreationCost() && plugin.getCreationCost() > 0) {
                        if (plugin.getCurrencyType() != CurrencyType.ITEM) {
                            EconomyUtils.addFunds(shop.getOwner(),player.getInventory(), plugin.getCreationCost());
                        } else {
                            ItemStack currencyDrop = plugin.getItemCurrency().clone();
                            currencyDrop.setAmount((int) plugin.getCreationCost());
                            shop.getChestLocation().getWorld().dropItemNaturally(shop.getChestLocation(), currencyDrop);
                        }
                    }
                }

                String message = ShopMessage.getMessage(shop.getType().toString(), "destroy", shop, player);
                if(message != null && !message.isEmpty())
                    player.sendMessage(message);
                shop.delete();
                plugin.getShopHandler().saveShops(shop.getOwnerUUID());

                return;
            }
            //player trying to break other players shop
            else {
                if (player.isOp() || (plugin.usePerms() && (player.hasPermission("shop.operator") || player.hasPermission("shop.destroy.other")))) {
                    PlayerDestroyShopEvent e = new PlayerDestroyShopEvent(player, shop);
                    plugin.getServer().getPluginManager().callEvent(e);

                    if (e.isCancelled()) {
                        event.setCancelled(true);
                        return;
                    }

                    String message = ShopMessage.getMessage(shop.getType().toString(), "opDestroy", shop, player);
                    if(message != null && !message.isEmpty())
                        player.sendMessage(message);
                    shop.delete();
                    plugin.getShopHandler().saveShops(shop.getOwnerUUID());
                } else
                    event.setCancelled(true);
            }
        } else if (plugin.getShopHandler().isChest(b)) {

            AbstractShop shop = plugin.getShopHandler().getShopByChest(b);
            if (shop == null) {
                return;
            }

            InventoryHolder ih = ((InventoryHolder)b.getState()).getInventory().getHolder();

            if (ih instanceof DoubleChest) {
                if(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))){

                    //the broken block was the initial chest with the sign
                    if(shop.getChestLocation().equals(b.getLocation())){
                        String message = ShopMessage.getMessage("interactionIssue", "destroyChest", null, player);
                        if(message != null && !message.isEmpty())
                            player.sendMessage(message);
                        event.setCancelled(true);
                        plugin.getTransactionListener().sendEffects(false, player, shop);
                    }
                    else {
                        PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), false);
                        Bukkit.getPluginManager().callEvent(e);

                        if(e.isCancelled()){
                            event.setCancelled(true);
                            return;
                        }
                        return;
                    }
                }
                else
                    event.setCancelled(true);
            }
            else{
                if(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) {
                    String message = ShopMessage.getMessage("interactionIssue", "destroyChest", null, player);
                    if(message != null && !message.isEmpty())
                        player.sendMessage(message);
                    plugin.getTransactionListener().sendEffects(false, player, shop);
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBreakBlockUnderShop(BlockBreakEvent event){
       //if the block under a chest has been broken, check that its a shop chest
        if(DisplayUtil.isChest(event.getBlock().getRelative(BlockFace.UP).getType())){
            AbstractShop shop = plugin.getShopHandler().getShopByChest(event.getBlock().getRelative(BlockFace.UP));
            if(shop != null){
                //if it is a shop chest, don't allow it to be broken unless its by the owner or someone with permission
                Player player = event.getPlayer();
                if(!(shop.getOwnerUUID().equals(player.getUniqueId()) || player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator")))){
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onShopExpansion(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        Player player = event.getPlayer();

        if (plugin.getShopHandler().isChest(b)) {
            ArrayList<BlockFace> doubleChestFaces = new ArrayList<>();
            doubleChestFaces.add(BlockFace.NORTH);
            doubleChestFaces.add(BlockFace.EAST);
            doubleChestFaces.add(BlockFace.SOUTH);
            doubleChestFaces.add(BlockFace.WEST);

            //find out if the player placed a chest next to an already active shop
            AbstractShop shop = plugin.getShopHandler().getShopTouchingBlock(b);
            if (shop == null || (b.getType() != shop.getChestLocation().getBlock().getType()))
                return;
            else if(b.getType() == Material.ENDER_CHEST)
                return;

            //owner is trying to
            if (shop.getOwnerUUID().equals(player.getUniqueId())) {
                PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), true);
                Bukkit.getPluginManager().callEvent(e);

                if(e.isCancelled()){
                    event.setCancelled(true);
                    return;
                }
                return;
            }
            //other player is trying to
            else {
                if (player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) {
                    PlayerResizeShopEvent e = new PlayerResizeShopEvent(player, shop, b.getLocation(), true);
                    Bukkit.getPluginManager().callEvent(e);

                    if(e.isCancelled()){
                        event.setCancelled(true);
                        return;
                    }

                } else
                    event.setCancelled(true);
            }
        }
    }

//    //allow players to place blocks that are occupied by large item displays
//    @EventHandler
//    public void onBlockPlaceAttempt(PlayerInteractEvent event) {
//        try {
//            if (event.getHand() == EquipmentSlot.OFF_HAND) {
//                return; // off hand version, ignore.
//            }
//        } catch (NoSuchMethodError error) {}
//
//        if(plugin.getDisplayType() != DisplayType.LARGE_ITEM)
//            return;
//        final Player player = event.getPlayer();
//
//        if (event.isCancelled())
//            return;
//
//        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
//            if(plugin.getShopHandler().isChest(event.getClickedBlock()))
//                return;
//            if(player.getItemInHand().getType().isBlock()){
//                Block toChange = event.getClickedBlock().getRelative(event.getBlockFace());
//                if(toChange.getType() != Material.AIR)
//                    return;
//                BlockFace[] directions = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
//                Block[] checks = {toChange, toChange.getRelative(BlockFace.DOWN)};
//                for(Block check : checks) {
//                    for (BlockFace dir : directions) {
//                        Block b = check.getRelative(dir);
//                        if (plugin.getShopHandler().isChest(b)) {
//                            AbstractShop shop = plugin.getShopHandler().getShopByChest(b);
//                            if (shop != null) {
//                                if (player.getUniqueId().equals(shop.getOwnerUUID()) || player.isOp() || (plugin.usePerms() && player.hasPermission("shop.operator"))) {
//                                    toChange.setType(player.getItemInHand().getType());
//                                    event.setCancelled(true);
//                                    if (player.getGameMode() == GameMode.SURVIVAL) {
//                                        ItemStack hand = player.getItemInHand();
//                                        hand.setAmount(hand.getAmount() - 1);
//                                        if (hand.getAmount() == 0)
//                                            hand.setType(Material.AIR);
//                                        event.getPlayer().setItemInHand(hand);
//                                    }
//                                }
//                                return;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
}