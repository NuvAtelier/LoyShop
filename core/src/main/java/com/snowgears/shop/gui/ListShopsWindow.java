package com.snowgears.shop.gui;

import com.snowgears.shop.Shop;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.util.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ListShopsWindow extends ShopGuiWindow {

    //List<Person> beerDrinkers = persons.stream()
    //        .filter(p -> p.getAge() > 16).collect(Collectors.toList());

    private List<AbstractShop> allShops;

    public ListShopsWindow(UUID player){

        super(player);

        //TODO save a list of all shops to a collection when opening this window for you to modify with filters

        this.title = Shop.getPlugin().getGuiHandler().getTitle(ShopGuiHandler.GuiTitle.LIST_SHOPS);

        this.page = Bukkit.createInventory(null, INV_SIZE, this.title);

        allShops = Shop.getPlugin().getShopHandler().getAllShops();
        //Collections.sort(allShops, new ShopItemComparator()); //this will be taken care of in inv contents

        initInvContents();

    }

    @Override
    protected void initInvContents() {
        super.initInvContents();
        this.clearInvBody();

        makeMenuBarUpper();
        makeMenuBarLower();

        ShopGuiHandler.GuiIcon guiSortIcon = Shop.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_SORT);
        switch (guiSortIcon){
            case MENUBAR_SORT_NAME_HIGH:
                Collections.sort(allShops, new ComparatorShopItemNameHigh());
                break;
            case MENUBAR_SORT_PRICE_LOW:
                Collections.sort(allShops, new ComparatorShopPriceLow());
                break;
            case MENUBAR_SORT_PRICE_HIGH:
                Collections.sort(allShops, new ComparatorShopPriceHigh());
                break;
            default:
                Collections.sort(allShops, new ComparatorShopItemNameLow());
                break;
        }

        //Collections.sort(shops, new ShopTypeComparator());

        //System.out.println(player.toString()+" number of shops "+shops.size());

        //TODO break up inventory into sections by type (by default. More sorting options to come)

        int startIndex = pageIndex * 36; //36 items is a full page in the inventory
        ItemStack icon;
        boolean added = true;

        for (int i=startIndex; i< allShops.size(); i++) {
            AbstractShop shop = allShops.get(i);
            icon = Shop.getPlugin().getGuiHandler().getIcon(ShopGuiHandler.GuiIcon.LIST_SHOP, null, shop);

            if(!this.addIcon(icon)){
                added = false;
                break;
            }
        }

        if(added){
            page.setItem(53, null);
        }
        else{
            page.setItem(53, this.getNextPageIcon());
        }
    }

    @Override
    protected void makeMenuBarUpper(){
        super.makeMenuBarUpper();

        //init the menu bar with the saved sort settings
        ShopGuiHandler.GuiIcon guiSortIcon = Shop.getPlugin().getGuiHandler().getIconFromOption(player, PlayerSettings.Option.GUI_SORT);
        ItemStack sortIcon = Shop.getPlugin().getGuiHandler().getIcon(guiSortIcon, player, null);
        page.setItem(3, sortIcon);

        //filter type - all, sell, buy, barter, gamble

        //filter type - in stock, out of stock, all
        // TODO this will also need to have stock variable calculated every time the shop is used
        // TODO also save and read this variable from shop files

        //search icon
        ItemStack searchIcon = Shop.getPlugin().getGuiHandler().getIcon(ShopGuiHandler.GuiIcon.HOME_SEARCH, null, null);
        page.setItem(8, searchIcon);
    }

    @Override
    protected void makeMenuBarLower(){
        super.makeMenuBarLower();
    }
}

