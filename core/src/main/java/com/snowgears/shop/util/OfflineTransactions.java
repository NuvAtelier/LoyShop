package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class OfflineTransactions {

    private UUID playerUUID;
    private long lastPlayed;
    private boolean isCalculating;
    private int numTransactions;
    private double totalProfit;
    private double totalSpent;
    private Map<ItemStack, Integer> itemsBought;
    private Map<ItemStack, Integer> itemsSold;

    public OfflineTransactions(UUID playerUUID, long lastPlayed){
        this.playerUUID = playerUUID;
        this.lastPlayed = lastPlayed;
        calculate();
    }

    private void calculate(){
        isCalculating = true;
        Shop.getPlugin().getLogHandler().calculateOfflineTransactions(this);
    }

    public UUID getPlayerUUID(){
        return playerUUID;
    }

    public long getLastPlayed(){
        return lastPlayed;
    }

    public boolean isCalculating(){
        return isCalculating;
    }
    public void setIsCalculating(boolean isCalculating){
        this.isCalculating = isCalculating;
    }
    public int getNumTransactions(){
        return numTransactions;
    }
    public void setNumTransactions(int numTransactions){
        this.numTransactions = numTransactions;
    }
    public double getTotalProfit(){
        return totalProfit;
    }
    public void setTotalProfit(double totalProfit){
        this.totalProfit = totalProfit;
    }
    public double getTotalSpent(){
        return totalSpent;
    }
    public void setTotalSpent(double totalSpent){
        this.totalSpent = totalSpent;
    }
    public Map<ItemStack, Integer> getItemsBought(){
        return itemsBought;
    }
    public void setItemsBought(Map<ItemStack, Integer> itemsBought){
        this.itemsBought = itemsBought;
    }
    public Map<ItemStack, Integer> getItemsSold(){
        return itemsSold;
    }
    public void setItemsSold(Map<ItemStack, Integer> itemsSold){
        this.itemsSold = itemsSold;
    }
}
