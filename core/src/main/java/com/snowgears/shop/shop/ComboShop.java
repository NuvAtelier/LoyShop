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

public class ComboShop extends AbstractShop {

    private double priceBuy;
    private double priceSell;

    public ComboShop(Location signLoc, UUID player, double pri, double priSell, int amt, Boolean admin, BlockFace facing) {
        super(signLoc, player, pri, amt, admin, facing);

        this.type = ShopType.COMBO;
        this.signLines = ShopMessage.getSignLines(this, this.type);
        this.priceBuy = pri;
        this.priceSell = priSell;
    }

    public String getPriceSellString() {
        return Shop.getPlugin().getPriceString(this.priceSell, false);
    }

    public String getPriceSellPerItemString() {
        double pricePer = this.getPriceSell() / this.getAmount();
        return Shop.getPlugin().getPriceString(pricePer, true);
    }

    public String getPriceComboString() {
        return Shop.getPlugin().getPriceComboString(this.price, this.priceSell, false);
    }

    public double getPriceSell(){
        return priceSell;
    }

    @Override
    protected int calculateStock(){
        stock = 0;
        if(this.isAdmin) {
            stock = Integer.MAX_VALUE;
        }
        else {
            double funds = EconomyUtils.getFunds(this.getOwner(), this.getInventory());
            if (this.getPrice() == 0)
                stock = Integer.MAX_VALUE;
            else{
                stock = (int)(funds / this.getPrice());
                // Check if we should show partial stock
                if (stock == 0 && Shop.getPlugin().getAllowPartialSales()) {
                    int stockRemaining = (int) funds / this.getPricePerItem();
                    if (stockRemaining >= 1) {
                        stock = 1;
                    }
                }
            }

            // Check if we have stock to sell items still, even if we don't have funds to buy items anymore
            if (stock == 0) {
                int itemsToSell = InventoryUtils.getAmount(this.getInventory(), this.getItemStack());
                stock = itemsToSell / this.getAmount();
                // Check if we should show partial stock
                if (stock == 0 && Shop.getPlugin().getAllowPartialSales()) {
                    int stockRemaining = (int) Math.floor(InventoryUtils.getAmount(this.getInventory(), this.getItemStack()) / this.getItemsPerPriceUnit());
                    System.out.println("items leftInShop: " + InventoryUtils.getAmount(this.getInventory(), this.getItemStack()));
                    System.out.println("getItemsPerPriceUnit: " + this.getItemsPerPriceUnit());
                    System.out.println("stockRemaining: " + stockRemaining);
                    if (stockRemaining >= 1) {
                        stock = 1;
                    }
                }
            }
        }
        return stock;
    }
}
