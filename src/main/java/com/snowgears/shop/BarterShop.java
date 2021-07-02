package com.snowgears.shop;

import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.util.InventoryUtils;
import com.snowgears.shop.util.ShopMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BarterShop extends AbstractShop {

    public BarterShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.type = ShopType.BARTER;
        this.signLines = ShopMessage.getSignLines(this, this.type);
    }

    //TODO incorporate # of orders at a time into this transaction
    @Override
    public TransactionError executeTransaction(int orders, Player player, boolean isCheck, ShopType transactionType) {

        this.isPerformingTransaction = true;
        TransactionError issue = null;

        ItemStack is = this.getItemStack();
        ItemStack is2 = this.getSecondaryItemStack();

        //check if shop has enough items
        if (!this.isAdmin()) {
            if(isCheck) {
                int shopItems = InventoryUtils.getAmount(this.getInventory(), is);
                if (shopItems < is.getAmount()) {
                    this.isPerformingTransaction = false;
                    return TransactionError.INSUFFICIENT_FUNDS_SHOP;
                }
            }
            else {
                //remove items from shop
                InventoryUtils.removeItem(this.getInventory(), is, this.getOwner());
            }
        }

        if(issue == null) {
            if(isCheck) {
                //check if player has enough barter items
                int playerItems = InventoryUtils.getAmount(player.getInventory(), is2);
                if (playerItems < is2.getAmount()) {
                    this.isPerformingTransaction = false;
                    return TransactionError.INSUFFICIENT_FUNDS_PLAYER;

                }
            }
            else {
                //remove barter items from player
                InventoryUtils.removeItem(player.getInventory(), is2, player);
            }
        }

        if(issue == null) {
            //check if shop has enough room to accept barter items
            if (!this.isAdmin()) {
                if(isCheck) {
                    boolean hasRoom = InventoryUtils.hasRoom(this.getInventory(), is2, this.getOwner());
                    if (!hasRoom) {
                        this.isPerformingTransaction = false;
                        return TransactionError.INVENTORY_FULL_SHOP;
                    }
                }
                else {
                    //add barter items to shop
                    InventoryUtils.addItem(this.getInventory(), is2, this.getOwner());
                }
            }
        }

        if(issue == null) {
            if(isCheck) {
                //check if player has enough room to accept items
                boolean hasRoom = InventoryUtils.hasRoom(player.getInventory(), is, player);
                if (!hasRoom) {
                    this.isPerformingTransaction = false;
                    return TransactionError.INVENTORY_FULL_PLAYER;
                }
            }
            else {
                //add items to player's inventory
                InventoryUtils.addItem(player.getInventory(), is, player);
            }
        }

        player.updateInventory();

        if(issue != null){
            this.isPerformingTransaction = false;
            return issue;
        }

        //if there are no issues with the test/check transaction
        if(issue == null && isCheck){

            PlayerExchangeShopEvent e = new PlayerExchangeShopEvent(player, this);
            Bukkit.getPluginManager().callEvent(e);

            if(e.isCancelled()) {
                this.isPerformingTransaction = false;
                return TransactionError.CANCELLED;
            }

            //run the transaction again without the check clause
            return executeTransaction(orders, player, false, transactionType);
        }
        this.isPerformingTransaction = false;
        setGuiIcon();
        return TransactionError.NONE;
    }

    @Override
    public boolean isInitialized() {
        return (item != null && secondaryItem != null);
    }

}
