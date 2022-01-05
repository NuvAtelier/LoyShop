package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ShopCreationProcess {

    private UUID processUUID;
    private UUID playerUUID;
    private Block clickedChest;
    private BlockFace clickedFace;
    private ItemStack itemStack;
    private ItemStack barterItemStack;
    private ShopType shopType;
    boolean isAdmin;
    private int itemAmount;
    private int barterItemAmount;
    private PricePair pricePair;

    private ChatCreationStep step;

    public ShopCreationProcess(Player player, Block clickedChest, BlockFace clickedFace){
        this.processUUID = UUID.randomUUID();
        this.playerUUID = player.getUniqueId();
        this.clickedChest = clickedChest;
        this.clickedFace = clickedFace;
        this.step = ChatCreationStep.SHOP_TYPE;
        //TODO calculate clicked face from player if blockface is UP or DOWN
        //TODO also that there is room for the sign on the clicked face
    }

    public Block getClickedChest() {
        return clickedChest;
    }

    public BlockFace getClickedFace() {
        return clickedFace;
    }

    public ShopType getShopType() {
        return shopType;
    }

    public void setShopType(ShopType shopType) {
        this.shopType = shopType;
        this.step = ChatCreationStep.ITEM_AMOUNT;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(int itemAmount) {
        this.itemAmount = itemAmount;
        if(this.shopType == ShopType.BARTER){
            this.step = ChatCreationStep.BARTER_CHEST_HIT;
        }
        else {
            this.step = ChatCreationStep.ITEM_PRICE;
        }
    }

    public int getBarterItemAmount() {
        return barterItemAmount;
    }

    public void setBarterItemAmount(int barterItemAmount){
        this.barterItemAmount = barterItemAmount;
        this.step = ChatCreationStep.FINISHED;
    }

    public PricePair getPricePair(){
        if(pricePair == null)
            this.pricePair = new PricePair(0, 0);
        return pricePair;
    }

    public ChatCreationStep getStep() {
        return step;
    }

    public void setPricePair(PricePair pricePair){
        this.pricePair = pricePair;
        if(this.shopType == ShopType.COMBO)
            this.step = ChatCreationStep.ITEM_PRICE_COMBO;
        else
            this.step = ChatCreationStep.FINISHED;
    }

    public void createShop(Player player){
        final ShopCreationProcess process = this;
        Shop.getPlugin().getServer().getScheduler().runTask(Shop.getPlugin(), new Runnable() {
            @Override
            public void run() {
                //TODO do some calculation here if clickedFace is filled with a block or UP / DOWN was clicked
                Block signBlock = clickedChest.getRelative(clickedFace);
                signBlock.setType(Material.OAK_WALL_SIGN);

                if(signBlock.getBlockData() instanceof WallSign) {
                    Directional wallSignData = (Directional) signBlock.getBlockData();
                    wallSignData.setFacing(clickedFace);
                    signBlock.setBlockData(wallSignData);
                }

                AbstractShop shop = ShopCreationUtil.createShop(Bukkit.getPlayer(playerUUID), clickedChest, clickedChest.getRelative(clickedFace), getPricePair(), itemAmount, isAdmin, shopType, clickedFace);
                if(shop == null) {
                    return;
                }
                shop.setItemStack(process.getItemStack());
                if(shopType == ShopType.BARTER){
                    shop.setSecondaryItemStack(process.getBarterItemStack());
                    //TODO make sure gamble amounts and barter and combo amounts are all correct
                }
                shop.updateSign();
                //shop.getDisplay().spawn();

                String message = ShopMessage.getUnformattedMessage(shopType.toString(), "create");
                message = ShopMessage.formatMessage(message, process, player);
                if (message != null && !message.isEmpty())
                    player.sendMessage(message);

                Shop.getPlugin().getTransactionListener().sendEffects(true, player, shop);
                Shop.getPlugin().getShopHandler().saveShops(shop.getOwnerUUID());
            }
        });
    }

    public UUID getUniqueID(){
        return processUUID;
    }

    public UUID getPlayerUUID(){
        return playerUUID;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public ItemStack getBarterItemStack() {
        return barterItemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemStack.setAmount(1);
    }

    public void setBarterItemStack(ItemStack barterItemStack) {
        this.barterItemStack = barterItemStack.clone();
        this.barterItemStack.setAmount(1);
        this.step = ChatCreationStep.BARTER_ITEM_AMOUNT;
    }

    public void setPrice(double price){
        if(pricePair == null)
            pricePair = new PricePair(price, 0);
        pricePair.setPrice(price);
        if(this.shopType == ShopType.COMBO)
            this.step = ChatCreationStep.ITEM_PRICE_COMBO;
        else
            this.step = ChatCreationStep.FINISHED;
    }

    public void setPriceCombo(double priceCombo){
        if(pricePair == null)
            pricePair = new PricePair(0, priceCombo);
        pricePair.setPriceCombo(priceCombo);
        this.step = ChatCreationStep.FINISHED;
    }

    public enum ChatCreationStep {

        SHOP_TYPE,

        ITEM_AMOUNT,

        ITEM_PRICE,

        ITEM_PRICE_COMBO,

        BARTER_CHEST_HIT,

        BARTER_ITEM_AMOUNT,

        FINISHED
    }


}
