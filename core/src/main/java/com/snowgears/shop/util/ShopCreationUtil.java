package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.event.PlayerCreateShopEvent;
import com.snowgears.shop.hook.TownyHook;
import com.snowgears.shop.hook.WorldGuardHook;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.SellShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;

public class ShopCreationUtil {

    public static boolean shopCanBeCreated(Player player, Block chest) {
        int numberOfShops = Shop.getPlugin().getShopHandler().getNumberOfShops(player);
        int buildPermissionNumber = Shop.getPlugin().getShopListener().getBuildLimit(player);

        if ((!Shop.getPlugin().usePerms() && !player.isOp()) || (Shop.getPlugin().usePerms() && !player.hasPermission("shop.operator"))) {
            if (numberOfShops >= buildPermissionNumber) {
                AbstractShop tempShop = new SellShop(null, player.getUniqueId(), 0, 0, false, BlockFace.NORTH);
                player.sendMessage(ShopMessage.getMessage("permission", "buildLimit", tempShop, player));
                return false;
            }
        }

        if (Shop.getPlugin().getWorldBlacklist().contains(chest.getWorld().getName())) {
            if (!(player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator")))) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "worldBlacklist", null, player));
                return false;
            }
        }

        //do a check for the WorldGuard region (optional hook)
        boolean canCreateShopInRegion = true;
        try {
            if(Shop.getPlugin().hookWorldGuard()) {
                canCreateShopInRegion = WorldGuardHook.canCreateShop(player, chest.getLocation());
            }
        } catch (NoClassDefFoundError e) {
            //tried to hook world guard but it was not registered
            e.printStackTrace();
        }

        //do a check for the Towny region (optional hook)
        try {
            if(Shop.getPlugin().hookTowny()) {
                canCreateShopInRegion = TownyHook.canCreateShop(player, chest.getLocation());
            }
        } catch (NoClassDefFoundError e) {
            //tried to hook towny but it was not registered
            e.printStackTrace();
        }

        if (!canCreateShopInRegion) {
            player.sendMessage(ShopMessage.getMessage("interactionIssue", "regionRestriction", null, player));
            return false;
        }

        return true;
    }

    public static AbstractShop createShop(Player player, Block chestBlock, Block signBlock, PricePair pricePair, int amount, boolean isAdmin, ShopType type, BlockFace signDirection){
        String playerMessage = null;
        final AbstractShop shop = AbstractShop.create(signBlock.getLocation(), player.getUniqueId(), pricePair.getPrice(), pricePair.getPriceCombo(), amount, isAdmin, type, signDirection);

        if (Shop.getPlugin().usePerms()) {
            if (!(player.hasPermission("shop.create." + type.toString().toLowerCase()) || player.hasPermission("shop.create")))
                playerMessage = ShopMessage.getMessage("permission", "create", shop, player);
        }

        if (type == ShopType.GAMBLE) {
            isAdmin = true;
            shop.setAdmin(true);
            if ((Shop.getPlugin().usePerms() && !player.hasPermission("shop.operator")) || (!Shop.getPlugin().usePerms() && !player.isOp())) {
                playerMessage = ShopMessage.getMessage("permission", "create", shop, player);
            }
        }

        //if players must pay to create shops, check that they have enough money first
        double cost = Shop.getPlugin().getCreationCost();
        if (cost > 0) {
            if (!EconomyUtils.hasSufficientFunds(player, player.getInventory(), cost)) {
                playerMessage = ShopMessage.getMessage("interactionIssue", "createInsufficientFunds", shop, player);
            }
        }

        if (player.isOp() || (Shop.getPlugin().usePerms() && player.hasPermission("shop.operator"))) {
            playerMessage = null;
        }

        //prevent players (even if they are OP) from creating a shop on a double chest with another player
        AbstractShop existingShop = Shop.getPlugin().getShopHandler().getShopByChest(chestBlock);
        if (existingShop != null && !existingShop.isAdmin()) {
            if (!existingShop.getOwnerUUID().equals(player.getUniqueId())) {
                playerMessage = ShopMessage.getMessage("interactionIssue", "createOtherPlayer", null, player);
            }
        }

        if (playerMessage != null) {
            if(!playerMessage.isEmpty())
                player.sendMessage(playerMessage);
            return null;
        }

        //removed all the direction checking code. just make sure its a container
        //make sure that the sign is in front of the chest, unless it is a shulker box
        if (chestBlock.getState() instanceof Container || (Shop.getPlugin().useEnderChests() && chestBlock.getType() == Material.ENDER_CHEST)) {
            //System.out.println("Chest of shop was a container.");
            existingShop = Shop.getPlugin().getShopHandler().getShopByChest(chestBlock);
            if (existingShop != null) {
                //if the block they are adding a sign to is already a shop, do not let them
                if (chestBlock.getLocation().equals(existingShop.getChestLocation())) {
                    String message = ShopMessage.getMessage("interactionIssue", "createOtherPlayer", null, player);
                    if (message != null && !message.isEmpty())
                        player.sendMessage(message);
                    return null;
                }
            }


            if (!(signBlock.getBlockData() instanceof WallSign)) {
                if (!signBlock.getType().toString().contains("_SIGN")) {
                    return null;
                }
                String wallSignString = signBlock.getType().toString().replaceAll("_SIGN", "_WALL_SIGN");
                signBlock.setType(Material.valueOf(wallSignString));

                Directional wallSignData = (Directional) signBlock.getBlockData();
                wallSignData.setFacing(signDirection);
                signBlock.setBlockData(wallSignData);
            }
            Sign signBlockState = (Sign) signBlock.getState();
            signBlockState.update();

            shop.setAdmin(isAdmin);
            shop.load();

            PlayerCreateShopEvent e = new PlayerCreateShopEvent(player, shop);
            Shop.getPlugin().getServer().getPluginManager().callEvent(e);

            if (e.isCancelled())
                return null;

            if (UtilMethods.isMCVersion17Plus() && Shop.getPlugin().getDisplayLightLevel() > 0) {
                Block displayBlock = shop.getChestLocation().getBlock().getRelative(BlockFace.UP);
                if (UtilMethods.materialIsNonIntrusive(displayBlock.getType())) {
                    displayBlock.setType(Material.LIGHT);
                    Light data = (Light) displayBlock.getBlockData();
                    data.setLevel(Shop.getPlugin().getDisplayLightLevel());
                    displayBlock.setBlockData(data);
                }
            }

            if (type == ShopType.GAMBLE) {
                shop.setItemStack(Shop.getPlugin().getGambleDisplayItem());
                shop.setAmount(1);
                Shop.getPlugin().getShopHandler().addShop(shop);
                shop.getDisplay().setType(DisplayType.LARGE_ITEM, false);
                shop.getDisplay().spawn(player);
                shop.updateSign();

                String message = ShopMessage.getMessage(shop.getType().toString(), "create", shop, player);
                if (message != null && !message.isEmpty())
                    player.sendMessage(message);
                Shop.getPlugin().getTransactionListener().sendEffects(true, player, shop);
                Shop.getPlugin().getShopHandler().saveShops(shop.getOwnerUUID());
                return null;
            }

            Shop.getPlugin().getShopHandler().addShop(shop);
            shop.updateSign();

            String message = ShopMessage.getMessage(type.toString(), "initialize", shop, player);
            if (message != null && !message.isEmpty())
                player.sendMessage(message);
            if (type == ShopType.BUY && Shop.getPlugin().allowCreativeSelection()) {
                message = ShopMessage.getMessage(type.toString(), "initializeAlt", shop, player);
                if (message != null && !message.isEmpty())
                    player.sendMessage(message);
            }
        }
        return shop;
    }

    public static ShopType getShopType(String input){
        ShopType type = null;
//        if (input.toLowerCase().contains(ShopMessage.getCreationWord("SELL")))
//            type = ShopType.SELL;
        if (input.toLowerCase().contains(ShopMessage.getCreationWord("BUY")))
            type = ShopType.BUY;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("BARTER")))
            type = ShopType.BARTER;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("GAMBLE")))
            type = ShopType.GAMBLE;
        else if (input.toLowerCase().contains(ShopMessage.getCreationWord("COMBO")))
            type = ShopType.COMBO;
        else
            type = ShopType.SELL;
        return type;
    }

    public static double getShopPrice(Player player, String input, ShopType shopType){
        double price = 0;
        if (Shop.getPlugin().getCurrencyType() == CurrencyType.VAULT) {
            try {
                int multiplyValue = UtilMethods.getMultiplyValue(input);
                String line3 = UtilMethods.cleanNumberText(input);

                if (line3.contains("."))
                    price = Double.parseDouble(line3);
                else
                    price = Long.parseLong(line3);

                price *= multiplyValue;
            } catch (NumberFormatException e) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
                return -1;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                price = Long.parseLong(line3);

            } catch (NumberFormatException e) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
                return -1;
            }
        }
        //only allow price to be zero if the type is selling
        //if (price < 0 || (price == 0 && !(type == ShopType.SELL))) {
        if (price < 0 || (price == 0 && shopType == ShopType.BARTER)) {
            player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
            return -1;
        }
        return price;
    }

    public static double getShopPriceCombo(Player player, String input, ShopType shopType){
        double priceCombo = 0;
        if (Shop.getPlugin().getCurrencyType() == CurrencyType.VAULT) {
            try {
                int multiplyValue = UtilMethods.getMultiplyValue(input);
                String line3 = UtilMethods.cleanNumberText(input);

                if (line3.contains("."))
                    priceCombo = Double.parseDouble(line3);
                else
                    priceCombo = Long.parseLong(line3);

                priceCombo *= multiplyValue;

            } catch (NumberFormatException e) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
                return -1;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);
                priceCombo = Long.parseLong(line3);
            } catch (NumberFormatException e) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
                return -1;
            }
        }
        return priceCombo;
    }

    public static PricePair getShopPricePair(Player player, String input, ShopType shopType){
        PricePair pricePair = null;
        double price = 0;
        double priceCombo = 0;
        if (Shop.getPlugin().getCurrencyType() == CurrencyType.VAULT) {
            try {
                int multiplyValue = UtilMethods.getMultiplyValue(input);
                String line3 = UtilMethods.cleanNumberText(input);

                String[] multiplePrices = line3.split(" ");
                if (multiplePrices.length > 1) {
                    if (multiplePrices[0].contains("."))
                        price = Double.parseDouble(multiplePrices[0]);
                    else
                        price = Long.parseLong(multiplePrices[0]);

                    if (multiplePrices[1].contains("."))
                        priceCombo = Double.parseDouble(multiplePrices[1]);
                    else
                        priceCombo = Long.parseLong(multiplePrices[1]);
                } else {
                    if (line3.contains("."))
                        price = Double.parseDouble(line3);
                    else
                        price = Long.parseLong(line3);
                }

                price *= multiplyValue;
                priceCombo *= multiplyValue;

            } catch (NumberFormatException e) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
                return null;
            }
        } else {
            try {
                String line3 = UtilMethods.cleanNumberText(input);

                String[] multiplePrices = line3.split(" ");
                if (multiplePrices.length > 1) {
                    price = Long.parseLong(multiplePrices[0]);
                    priceCombo = Long.parseLong(multiplePrices[1]);
                } else {
                    price = Long.parseLong(line3);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
                return null;
            }
        }
        //only allow price to be zero if the type is selling
        //if (price < 0 || (price == 0 && !(type == ShopType.SELL))) {
        if (price < 0 || (price == 0 && shopType == ShopType.BARTER)) {
            player.sendMessage(ShopMessage.getMessage("interactionIssue", "line3", null, player));
            return null;
        }
        return new PricePair(price, priceCombo);
    }

    public static boolean getShopIsAdmin(String input){
        if (input.toLowerCase().contains(ShopMessage.getCreationWord("ADMIN")))
            return true;
        return false;
    }
}
