package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class GambleShop extends AbstractShop {

    private ItemStack gambleItem;

    public GambleShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.isAdmin = true;
        this.type = ShopType.GAMBLE;
        this.signLines = ShopMessage.getSignLines(this, this.type);
        setGambleItem();
        this.setAmount(this.gambleItem.getAmount());
    }

    // Called upon a successful gamble transaction
    public void shuffleGambleItem(Player player){
        isPerformingTransaction = true;
        this.setItemStack(gambleItem.clone());
        this.setAmount(gambleItem.getAmount());
        final DisplayType initialDisplayType = this.getDisplay().getType();
        this.getDisplay().setType(DisplayType.ITEM, false);
        setGambleItem();
        this.getDisplay().spawn(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                setItemStack(Shop.getPlugin().getGambleDisplayItem());
                if(initialDisplayType == null) {
                    display.setType(Shop.getPlugin().getDisplayType(), false);
                    getDisplay().spawn(player);
                }
                else {
                    display.setType(initialDisplayType, false);
                    getDisplay().spawn(player);
                }
                isPerformingTransaction = false;
            }
        }.runTaskLater(Shop.getPlugin(), 20);
    }

    public void setGambleItem(){
        this.gambleItem = Shop.getPlugin().getDisplayListener().getRandomItem(this);
    }

    public ItemStack getGambleItem(){
        return gambleItem;
    }
}
