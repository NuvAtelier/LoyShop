package com.snowgears.shop.util;

import com.snowgears.shop.shop.AbstractShop;
import org.bukkit.entity.Player;

public class PlaceholderContext {
    private final AbstractShop shop;
    private final Player player;
    private final boolean forSign;

    public PlaceholderContext(AbstractShop shop, Player player, boolean forSign) {
        this.shop = shop;
        this.player = player;
        this.forSign = forSign;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isForSign() {
        return forSign;
    }
}


