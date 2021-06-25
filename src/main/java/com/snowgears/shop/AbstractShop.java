package com.snowgears.shop;

import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.util.*;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.snowgears.shop.util.UtilMethods.isMCVersion17Plus;

public abstract class AbstractShop {

    protected Location signLocation;
    protected Location chestLocation;
    protected UUID owner;
    protected ItemStack item;
    protected ItemStack secondaryItem;
    protected AbstractDisplay display;
    protected double price;
    protected int amount;
    protected boolean isAdmin;
    protected ShopType type;
    protected String[] signLines;
    protected boolean signLinesRequireRefresh;
    protected boolean isPerformingTransaction;
    protected ItemStack guiIcon;

    public AbstractShop(Location signLoc, UUID player, double pri, int amt, Boolean admin) {
        signLocation = signLoc;
        owner = player;
        price = pri;
        amount = amt;
        isAdmin = admin;
        item = null;

        display = DisplayUtil.getDisplayForNMSVersion(this.signLocation);

        if(isAdmin){
            owner = Shop.getPlugin().getShopHandler().getAdminUUID();
        }

        if(signLocation != null) {
            try {
                WallSign sign = (WallSign) signLocation.getBlock().getBlockData();
                chestLocation = signLocation.getBlock().getRelative(sign.getFacing().getOppositeFace()).getLocation();
            } catch(ClassCastException cce){
                signLocation = null;
                chestLocation = null;
            }
        }
    }

    public static AbstractShop create(Location signLoc, UUID player, double pri, double priCombo, int amt, Boolean admin, ShopType shopType) {

        switch(shopType){
            case SELL:
                return new SellShop(signLoc, player, pri, amt, admin);
            case BUY:
                return new BuyShop(signLoc, player, pri, amt, admin);
            case BARTER:
                return new BarterShop(signLoc, player, pri, amt, admin);
            case GAMBLE:
                return new GambleShop(signLoc, player, pri, amt, admin);
            case COMBO:
                return new ComboShop(signLoc, player, pri, priCombo, amt, admin);
        }
        return null;
    }

    //abstract methods that must be implemented in each shop subclass

    public abstract TransactionError executeTransaction(int orders, Player player, boolean isCheck, ShopType transactionType);

    public int getStock() {
        if(this.getInventory() == null || this.getItemStack() == null)
            return 0;
        return InventoryUtils.getAmount(this.getInventory(), this.getItemStack()) / this.getAmount();
    }

    public boolean isInitialized(){
        return (item != null);
    }

    //getter methods

    public Location getSignLocation() {
        return signLocation;
    }

    public WallSign getSign(){
        BlockData signBlockData = this.getSignLocation().getBlock().getBlockData();
        if(signBlockData instanceof WallSign){
            return (WallSign)signBlockData;
        }
        return null;
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public Inventory getInventory() {
        if(chestLocation == null || signLocation == null)
            return null;
        Block chestBlock = chestLocation.getBlock();
        if(chestBlock.getType() == Material.ENDER_CHEST) {
            OfflinePlayer ownerPlayer = this.getOwner();
            if(ownerPlayer != null)
                return Shop.getPlugin().getEnderChestHandler().getInventory(ownerPlayer);
        }
        else if(chestBlock.getState() instanceof InventoryHolder){
            return ((InventoryHolder)(chestBlock.getState())).getInventory();
        }
        return null;
    }

    public UUID getOwnerUUID() {
        return owner;
    }

    public String getOwnerName() {
        if(this.isAdmin())
            return "admin";
        if (this.getOwner() != null)
            return Bukkit.getOfflinePlayer(this.owner).getName();
        return ChatColor.RED + "CLOSED";
    }

    public OfflinePlayer getOwner() {
        return Bukkit.getOfflinePlayer(this.owner);
    }

    public ItemStack getItemStack() {
        if (item != null) {
            ItemStack is = item.clone();
            is.setAmount(this.getAmount());
            return is;
        }
        return null;
    }

    public ItemStack getSecondaryItemStack() {
        if (secondaryItem != null) {
            ItemStack is = secondaryItem.clone();
            is.setAmount((int)this.getPrice());
            return is;
        }
        return null;
    }

    public AbstractDisplay getDisplay() {
        return display;
    }

    public double getPrice() {
        return price;
    }

    public String getPriceString() {
        return Shop.getPlugin().getPriceString(this.price, false);
    }

    public String getPricePerItemString() {
        double pricePer = this.getPrice() / this.getAmount();
        return Shop.getPlugin().getPriceString(pricePer, true);
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public ShopType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public BlockFace getFacing(){
        if(signLocation.getBlock().getBlockData() instanceof WallSign) {
            BlockData signData =  signLocation.getBlock().getBlockData();
            if(signData instanceof Directional)
                return ((Directional) signData).getFacing();
        }
        return null;
    }

    public ItemStack getGuiIcon(){
        return guiIcon;
    }

    //setter methods

    public void setItemStack(ItemStack is) {
        this.item = is.clone();
        if(!Shop.getPlugin().checkItemDurability()) {
            ItemMeta itemMeta = item.getItemMeta();
            if(itemMeta instanceof Damageable){
                Damageable damageableItem = (Damageable)itemMeta;
                damageableItem.setDamage(0); //set item to full durability
                item.setItemMeta(itemMeta);
            }
        }
        //this.display.spawn();
        setGuiIcon();
    }

    public void setSecondaryItemStack(ItemStack is) {
        this.secondaryItem = is.clone();
        if(!Shop.getPlugin().checkItemDurability()) {
            ItemMeta itemMeta = secondaryItem.getItemMeta();
            if(itemMeta instanceof Damageable){
                Damageable damageableItem = (Damageable)itemMeta;
                damageableItem.setDamage(0); //set secondary item to full durability
                secondaryItem.setItemMeta(itemMeta);
            }
        }
        //this.display.spawn();
        setGuiIcon();
    }

    public void setOwner(UUID newOwner){
        this.owner = newOwner;
    }

    public void setPrice(double price){
        this.price = price;
    }

    public void setAmount(int amount){
        this.amount = amount;
    }

    //TODO make all of this text configurable from the GUI config file
    //TODO use this to build GUIs more efficiently
    public void setGuiIcon(){
        guiIcon = this.getItemStack().clone();
        guiIcon.setAmount(1);

        List<String> lore = new ArrayList<>();
        lore.add("Type: " + this.getType().toString().toUpperCase());
        if(this.getType() == ShopType.BARTER)
            lore.add("Price: "+(int)this.getPrice() + " " + Shop.getPlugin().getItemNameUtil().getName(this.getSecondaryItemStack()));
        else if(this.getType() == ShopType.BUY)
            lore.add("Pays: " + this.getPriceString());
        else
            lore.add("Price: " + this.getPriceString());
        if(!this.isAdmin()) {
            lore.add("Stock: " + this.getStock());
        }
        lore.add("Location: " + UtilMethods.getCleanLocation(this.getSignLocation(), true));

        //TODO encorporate gambling shops and bartering shops better

        String name = UtilMethods.getItemName(this.getItemStack()) + " (x" + this.getAmount() + ")";
        ItemMeta iconMeta = guiIcon.getItemMeta();
        iconMeta.setDisplayName(name);
        iconMeta.setLore(lore);

        guiIcon.setItemMeta(iconMeta);
    }

    public int getItemDurabilityPercent(){
        ItemStack item = this.getItemStack().clone();
        return UtilMethods.getDurabilityPercent(item);
    }

    public int getSecondaryItemDurabilityPercent(){
        ItemStack item = this.getSecondaryItemStack().clone();
        return UtilMethods.getDurabilityPercent(item);
    }

    public void setSignLinesRequireRefresh(boolean signLinesRequireRefresh){
        this.signLinesRequireRefresh = signLinesRequireRefresh;
    }

    public boolean getSignLinesRequireRefresh(){
        return this.signLinesRequireRefresh;
    }

    public boolean isPerformingTransaction(){
        return isPerformingTransaction;
    }

    //common base methods to all shops

    public void updateSign() {

        signLines = ShopMessage.getSignLines(this, this.type);

        Shop.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(Shop.getPlugin(), new Runnable() {
            public void run() {

                Sign signBlock = (Sign) signLocation.getBlock().getState();

                String[] lines = signLines.clone();

                if (!isInitialized()) {
                    signBlock.setLine(0, ChatColor.RED + ChatColor.stripColor(lines[0]));
                    signBlock.setLine(1, ChatColor.RED + ChatColor.stripColor(lines[1]));
                    signBlock.setLine(2, ChatColor.RED + ChatColor.stripColor(lines[2]));
                    signBlock.setLine(3, ChatColor.RED + ChatColor.stripColor(lines[3]));
                } else {
                    signBlock.setLine(0, lines[0]);
                    signBlock.setLine(1, lines[1]);
                    signBlock.setLine(2, lines[2]);
                    signBlock.setLine(3, lines[3]);
                }

                if(isMCVersion17Plus()) {
                    if (Shop.getPlugin().getGlowingSignText()) {
                        signBlock.setGlowingText(true);
                    }
                    else{
                        signBlock.setGlowingText(false);
                    }
                }

                signBlock.update(true);
            }
        }, 2L);
    }

    public void delete() {
        display.remove(null);

        if(UtilMethods.isMCVersion17Plus() && Shop.getPlugin().getDisplayLightLevel() > 0) {
            Block displayBlock = this.getChestLocation().getBlock().getRelative(BlockFace.UP);
            displayBlock.setType(Material.AIR);
        }

        Block b = this.getSignLocation().getBlock();
        if (b.getBlockData() instanceof WallSign) {
            Sign signBlock = (Sign) b.getState();
            signBlock.setLine(0, "");
            signBlock.setLine(1, "");
            signBlock.setLine(2, "");
            signBlock.setLine(3, "");
            signBlock.update(true);
        }

        //finally remove the shop from the shop handler
        Shop.getPlugin().getShopHandler().removeShop(this);
    }

    public void teleportPlayer(Player player){
        if(player == null)
            return;

        BlockFace face = this.getFacing();
        Location loc = this.getSignLocation().getBlock().getRelative(face).getLocation().add(0.5, 0, 0.5);
        loc.setYaw(UtilMethods.faceToYaw(face.getOppositeFace()));
        loc.setPitch(25.0f);

        player.teleport(loc);
    }

    //TODO you may have to override this in other shop types like COMBO or GAMBLE
    public void printSalesInfo(Player player) {
        //player.sendMessage("");

        String message = ShopMessage.getUnformattedMessage(this.getType().toString(), "descriptionItem");
        if(message != null && !message.isEmpty())
            formatAndSendFancyMessage(message, player);

        if (this.getType() == ShopType.BARTER) {
            message = ShopMessage.getUnformattedMessage(this.getType().toString(), "descriptionBarterItem");
            if(message != null && !message.isEmpty())
                formatAndSendFancyMessage(message, player);
        }
        //player.sendMessage("");


        if(price != 0) {
            message = ShopMessage.getMessage(this.getType().toString(), "descriptionPrice", this, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);

            message = ShopMessage.getMessage(this.getType().toString(), "descriptionPricePerItem", this, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);
            //player.sendMessage("");
        }

        if(this.isAdmin()){
            message = ShopMessage.getMessage("description", "stockAdmin", this, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);
        }
        else {
            message = ShopMessage.getMessage("description", "stock", this, player);
            if(message != null && !message.isEmpty())
                player.sendMessage(message);
        }

        return;
    }

    protected void formatAndSendFancyMessage(String message, Player player){
        if(message == null)
            return;

        String[] parts = message.split("(?=&[0-9A-FK-ORa-fk-or])");
        TextComponent fancyMessage = new TextComponent("");

        for(String part : parts){
            ComponentBuilder builder = new ComponentBuilder("");
            org.bukkit.ChatColor cc = UtilMethods.getChatColor(part);
            if(cc != null)
                part = part.substring(2, part.length());
            boolean barterItem = false;
            if(part.contains("[barter item]"))
                barterItem = true;
            part = ShopMessage.formatMessage(part, this, player, false);
            part = ChatColor.stripColor(part);
            builder.append(part);
            if(cc != null) {
                builder.color(ChatColor.valueOf(cc.name()));
            }

            if(part.startsWith("[")) {
                String itemJson;
                if (barterItem) {
                    itemJson = ReflectionUtil.convertItemStackToJson(this.secondaryItem);
                } else {
                    itemJson = ReflectionUtil.convertItemStackToJson(this.item);
                }
                // Prepare a BaseComponent array with the itemJson as a text component
                BaseComponent[] hoverEventComponents = new BaseComponent[]{ new TextComponent(itemJson) }; // The only element of the hover events basecomponents is the item json
                HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_ITEM, hoverEventComponents);

                builder.event(event);
            }

            for(BaseComponent b : builder.create()) {
                fancyMessage.addExtra(b);
            }
        }

        //use special ComponentSender for MC 1.8+ and regular way for MC 1.7
        try {
            if (Material.AIR != Material.ARMOR_STAND) {
                player.spigot().sendMessage(fancyMessage);
            }
        } catch (NoSuchFieldError e) {
            player.sendMessage(fancyMessage.getText());
        }
    }
}
