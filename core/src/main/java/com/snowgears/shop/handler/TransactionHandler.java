
package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.hook.WorldGuardHook;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;


public class TransactionHandler {

    private Shop plugin;
    private HashMap<Location, UUID> shopMessageCooldown = new HashMap<>(); //shop location, shop owner

    public TransactionHandler(Shop instance) {
        plugin = instance;
    }

    public void executeTransactionFromEvent(PlayerInteractEvent event, AbstractShop shop, boolean fullStackOrder){
        Player player = event.getPlayer();

        if(shop.isPerformingTransaction()) {
            String message = ShopMessage.getMessage("interactionIssue", "useShopAlreadyInUse", shop, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);
            event.setCancelled(true);
            return;
        }

        boolean canUseShopInRegion = true;
        try {
            canUseShopInRegion = WorldGuardHook.canUseShop(player, shop.getSignLocation());
        } catch(NoClassDefFoundError e) {}

        //check that player can use the shop if it is in a WorldGuard region
        if(!canUseShopInRegion){
            String message = ShopMessage.getMessage("interactionIssue", "regionRestriction", null, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);
            event.setCancelled(true);
            return;
        }

        //delete shop if it does not have a chest attached to it
        if(!(plugin.getShopHandler().isChest(shop.getChestLocation().getBlock()))){
            plugin.getLogger().warning("Deleting Shop because chest does not exist! " + shop);
            shop.delete();
            return;
        }

        //do not allow gamble shops to have full stack orders
        if(shop.getType() == ShopType.GAMBLE)
            fullStackOrder = false;

        //player did not click their own shop
        if (!shop.getOwnerName().equals(player.getName()) || Shop.getPlugin().getDebug_allowUseOwnShop()) {

            if (plugin.usePerms() && !(player.hasPermission("shop.use."+shop.getType().toString().toLowerCase()) || player.hasPermission("shop.use"))) {
                if (!player.hasPermission("shop.operator")) {
                    String message = ShopMessage.getMessage("permission", "use", shop, player);
                    if(message != null && !message.isEmpty())
                        player.sendMessage(message);
                    return;
                }
            }
            //for COMBO shops, shops can execute either a BUY or a SELL depending on the side of sign that was clicked
            if(shop.getType() == ShopType.COMBO){
                int clickedSide = UtilMethods.calculateSideFromClickedSign(player, event.getClickedBlock());
                //clicked left side of sign
                if(clickedSide >= 0){
                    if(plugin.inverseComboShops())
                        executeTransactionSequence(player, shop, ShopType.SELL, fullStackOrder);
                    else
                        executeTransactionSequence(player, shop, ShopType.BUY, fullStackOrder);
                }
                //clicked right side of sign
                else{
                    if(plugin.inverseComboShops())
                        executeTransactionSequence(player, shop, ShopType.BUY, fullStackOrder);
                    else
                        executeTransactionSequence(player, shop, ShopType.SELL, fullStackOrder);
                }
            }
            else {
                executeTransactionSequence(player, shop, shop.getType(), fullStackOrder);
            }
        } else {
            String message = ShopMessage.getMessage("interactionIssue", "useOwnShop", shop, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);
            sendEffects(false, player, shop);
        }
        event.setCancelled(true);
    }

    private void executeTransactionSequence(Player player, AbstractShop shop, ShopType actionType, boolean fullStackOrder){
        Transaction transaction = new Transaction(player, shop, actionType);

        // Set the desired purchase amount if we are a full stack order
        if (fullStackOrder) {
            transaction.negotiatePurchase(64);
        } else {
            transaction.negotiatePurchase();
        }

        // Verify the transaction is possible
        TransactionError issue = transaction.verify();
        // If it is possible, go ahead and execute it, extra check just in case there is an issue (shouldn't ever happen, but who knows)
        if (issue == TransactionError.NONE) { issue = transaction.execute(); }

        // If there was an issue with the transaction, send the error message and bail out early
        if (issue != TransactionError.NONE) {
            //there was an issue when checking transaction, send reason to player
            this.sendErrorMessage(player, shop, actionType, transaction);
            return;
        }

        //the transaction has finished and the exchange event has not been cancelled
        sendExchangeMessagesAndLog(shop, player, actionType, transaction);
        sendEffects(true, player, shop);
        //make sure to update the shop sign, but only if the sign lines use a variable that requires a refresh (like stock that is dynamically updated)
        if(shop.getSignLinesRequireRefresh())
            shop.updateSign();
    }

    private void sendErrorMessage(Player player, AbstractShop shop, ShopType actionType, Transaction transaction) {
        String message = null;
        switch (transaction.getError()) {
            case INSUFFICIENT_FUNDS_SHOP:
                if (!shop.isAdmin()) {
                    Player owner = shop.getOwner().getPlayer();
                    //the shop owner is online
                    if (owner != null && notifyOwner(shop)) {
                        ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(player, PlayerSettings.Option.NOTIFICATION_STOCK);

                        if (guiIcon != null && guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON) {
                            String ownerMessage = ShopMessage.getMessage(actionType.toString(), "ownerNoStock", shop, owner);
                            if (ownerMessage != null && !ownerMessage.isEmpty())
                                owner.sendMessage(ownerMessage);
                        }
                    }
                }
                message = ShopMessage.getMessage(actionType.toString(), "shopNoStock", shop, player);
                break;
            case INSUFFICIENT_FUNDS_PLAYER:
                message = ShopMessage.getMessage(actionType.toString(), "playerNoStock", shop, player);
                break;
            case INVENTORY_FULL_SHOP:
                if (!shop.isAdmin()) {
                    Player owner = shop.getOwner().getPlayer();
                    //the shop owner is online
                    if (owner != null && notifyOwner(shop)) {
                        ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(player, PlayerSettings.Option.NOTIFICATION_STOCK);

                        if (guiIcon != null && guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON) {
                            String ownerMessage = ShopMessage.getMessage(actionType.toString(), "ownerNoSpace", shop, owner);
                            if (ownerMessage != null && !ownerMessage.isEmpty())
                                owner.sendMessage(ownerMessage);
                        }
                    }
                }
                message = ShopMessage.getMessage(actionType.toString(), "shopNoSpace", shop, player);
                break;
            case INVENTORY_FULL_PLAYER:
                message = ShopMessage.getMessage(actionType.toString(), "playerNoSpace", shop, player);
                break;
        }

        // Since there was an error during the transaction, send the message, then exit the transaction early.
        if (message != null && !message.isEmpty())
            player.sendMessage(message);
        sendEffects(false, player, shop);
    }

    private void sendExchangeMessagesAndLog(AbstractShop shop, Player player, ShopType transactionType, Transaction transaction) {

        double price = transaction.getPrice();
        String message = getMessageFromOrders(shop, player, transactionType, "user", price, transaction);

        ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(player, PlayerSettings.Option.NOTIFICATION_SALE_USER);
        if(guiIcon != null && guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON) {
            if(message != null && !message.isEmpty()) {
                ShopMessage.embedAndSendHoverItemsMessage(message, player, transaction.getItems());
//                player.sendMessage(message);
            }
        }

        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
        if ((owner != null) && (!shop.isAdmin())) {
            message = getMessageFromOrders(shop, player, transactionType, "owner", price, transaction);

            guiIcon = plugin.getGuiHandler().getIconFromOption(owner, PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
            if(guiIcon != null && guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON) {
                if(message != null && !message.isEmpty())
                    ShopMessage.embedAndSendHoverItemsMessage(message, owner, transaction.getItems());
//                    owner.sendMessage(message);
            }
        }

        int amount = transaction.getAmount();

        plugin.getLogHandler().logTransaction(player, shop, transactionType, price, amount);
    }

    private String getMessageFromOrders(AbstractShop shop, Player player, ShopType transactionType, String subKey, double price, Transaction transaction){
        String message = ShopMessage.getUnformattedMessage(transactionType.toString(), subKey);
        String priceStr = Shop.getPlugin().getPriceString(price, false);
        message = message.replace("[price]", ""+priceStr);

        if(shop.getItemStack() != null) {
            //int amount = shop.getItemStack().getAmount() * orders;
            int amount = transaction.getAmount();
            message = message.replace("[item amount]", "" + amount);
        }
        if(shop.getType() == ShopType.BARTER) {
           // int amount = shop.getSecondaryItemStack().getAmount() * transactions.size();
            int amount = (int) transaction.getPrice();
            message = message.replace("[barter item amount]", "" + amount);
        }
        message = ShopMessage.formatMessage(message, shop, player, false);
        return message;
    }

    public void sendEffects(boolean success, Player player, AbstractShop shop){
        try {
            if (success) {
                if (plugin.playSounds()) {
                    try {
                        player.playSound(shop.getSignLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                    } catch (NoSuchFieldError e) {}
                }
                if (plugin.playEffects())
                    player.getWorld().playEffect(shop.getChestLocation(), Effect.STEP_SOUND, Material.EMERALD_BLOCK);
            } else {
                if (plugin.playSounds())
                    player.playSound(shop.getSignLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 1.0F);
                if (plugin.playEffects())
                    player.getWorld().playEffect(shop.getChestLocation(), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);
            }
        } catch (Error e){
        } catch (Exception e) {}
    }

    private boolean notifyOwner(final AbstractShop shop){
        if(shop.isAdmin())
            return false;
        if(shopMessageCooldown.containsKey(shop.getSignLocation()))
            return false;
        else{
            shopMessageCooldown.put(shop.getSignLocation(), shop.getOwnerUUID());

            new BukkitRunnable() {
                @Override
                public void run() {
                    if(shop != null){
                        if(shopMessageCooldown.containsKey(shop.getSignLocation())){
                            shopMessageCooldown.remove(shop.getSignLocation());
                        }
                    }
                    //TODO if shop is null, should you clear the entire cooldown list so that that location isn't messed up?
                }
            }.runTaskLater(this.plugin, 2400); //make cooldown 2 minutes
        }
        return true;
    }
}