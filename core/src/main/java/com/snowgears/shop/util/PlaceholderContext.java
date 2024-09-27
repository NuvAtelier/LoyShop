package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderContext {
    private final AbstractShop shop;
    private final Player player;
    private final boolean forSign;
    private final List<ItemStack> items;
    private final ShopCreationProcess process;

    public PlaceholderContext(AbstractShop shop, Player player, boolean forSign, List<ItemStack> items, ShopCreationProcess process) {
        this.shop = shop;
        this.player = player;
        this.forSign = forSign;
        this.process = process;

        if (items == null) this.items = new ArrayList<>();
        else this.items = items;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public Player getPlayer() {
        return player;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public ShopCreationProcess getProcess() {
        return process;
    }

    public boolean isForSign() {
        return forSign;
    }
}


