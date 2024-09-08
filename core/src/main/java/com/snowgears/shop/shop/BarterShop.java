package com.snowgears.shop.shop;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

import static com.snowgears.shop.shop.BarterShop.BarterType.EXPERIENCE;
import static com.snowgears.shop.shop.BarterShop.BarterType.ITEM;

public class BarterShop extends AbstractShop {

    private ItemStack originalItem;
    private BarterType barterType;

    public BarterShop(Location signLoc, UUID player, double pri, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.type = ShopType.BARTER;
        this.barterType = ITEM;
        this.signLines = ShopMessage.getSignLines(this, this.type);
    }

    @Override
    public void setItemStack(ItemStack is) {
        super.setItemStack(is);
        if(originalItem == null){
            originalItem = is.clone();
        }
    }

    @Override
    public boolean isInitialized() {
        return (item != null && secondaryItem != null);
    }

    public enum BarterType{
        ITEM, EXPERIENCE
    }

    public void cycleBarterType(){
        //if shops are already using experience as the main currency, don't allow barter shops to barter experience (that would be a sell shop)
        if(Shop.getPlugin().getCurrencyType() == CurrencyType.EXPERIENCE)
            return;

        if(this.barterType == ITEM){
            this.barterType = EXPERIENCE;
        }
        else if(this.barterType == EXPERIENCE){
            this.barterType = ITEM;
        }
    }
}
