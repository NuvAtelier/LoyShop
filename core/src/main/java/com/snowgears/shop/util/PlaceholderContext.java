package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderContext {
    private AbstractShop shop;
    private Player player;
    private boolean forSign = false;
    private ItemStack item = null;
    private ShopCreationProcess process;
    private OfflineTransactions offlineTransactions;

    // Create empty Placeholder Context
    public PlaceholderContext() { }

    public void setShop(AbstractShop shop) {
        this.shop = shop;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setProcess(ShopCreationProcess process) {
        this.process = process;
    }

    public ShopCreationProcess getProcess() {
        return process;
    }

    public void setOfflineTransactions(OfflineTransactions offlineTransactions) {
        this.offlineTransactions = offlineTransactions;
    }

    public OfflineTransactions getOfflineTransactions() {
        return offlineTransactions;
    }

    public void setForSign(boolean forSign) {
        this.forSign = forSign;
    }

    public boolean isForSign() {
        return forSign;
    }
}


