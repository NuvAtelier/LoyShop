package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.handler.ShopGuiHandler;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.ShopType;
import de.tr7zw.changeme.nbtapi.NBT;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ShopMessage {

    private final static Shop plugin = Shop.getPlugin();

    private static final Map<String, Function<PlaceholderContext, TextComponent>> placeholders = new HashMap<>();
    // Regex pattern to identify placeholders within square brackets, e.g., [owner]
    private static final String COLOR_CODE_REGEX = "([&§][0-9A-FK-ORa-fk-or])";
    private static final String HEX_CODE_REGEX = "#[0-9a-fA-F]{6}";
    private static final String PLACEHOLDER_REGEX = "(\\[[^\\[\\]]+\\])|([^&\\[]+)";
    private static final String MESSAGE_PARTS_REGEX = COLOR_CODE_REGEX + "|" + HEX_CODE_REGEX + "|" + PLACEHOLDER_REGEX + "|.";

    private static HashMap<String, String> messageMap = new HashMap<String, String>();
    private static HashMap<String, String[]> shopSignTextMap = new HashMap<String, String[]>();
    private static HashMap<String, List<String>> displayTextMap = new HashMap<String, List<String>>();
    private static String freePriceWord;
    private static String adminStockWord;
    private static String serverDisplayName;
    private static HashMap<String, String> creationWords = new HashMap<String, String>();
    private static YamlConfiguration chatConfig;
    private static YamlConfiguration signConfig;
    private static YamlConfiguration displayConfig;

    public ShopMessage(Shop plugin) {
        File chatConfigFile = new File(plugin.getDataFolder(), "chatConfig.yml");
        chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);
        File signConfigFile = new File(plugin.getDataFolder(), "signConfig.yml");
        signConfig = YamlConfiguration.loadConfiguration(signConfigFile);
        File displayConfigFile = new File(plugin.getDataFolder(), "displayConfig.yml");
        displayConfig = YamlConfiguration.loadConfiguration(displayConfigFile);

        loadMessagesFromConfig();
        loadSignTextFromConfig();
        loadDisplayTextFromConfig();
        loadCreationWords();

        freePriceWord = signConfig.getString("sign_text.zeroPrice");
        adminStockWord = signConfig.getString("sign_text.adminStock");
        serverDisplayName = signConfig.getString("sign_text.serverDisplayName");

        // Load in our placeholders
        this.loadPlaceholders();
    }

    /**
     * Registers a placeholder with its corresponding value retrieval function.
     *
     * @param placeholder The placeholder string without brackets, e.g., "owner"
     * @param valueFunction A function that takes a PlaceholderContext instance and returns the replacement string
     */
    public static void registerPlaceholder(String placeholder, Function<PlaceholderContext, TextComponent> valueFunction) {
        placeholders.put(placeholder.toLowerCase(), valueFunction);
    }

    /**
     * Attempts to replace a single placeholder within a message.
     *
     * @param placeholder The placeholder string without brackets, e.g., "owner"
     * @param context     The PlaceholderContext instance containing Shop and Player
     * @return The replacement string or an empty string if replacement fails
     */
    public static TextComponent replacePlaceholder(String placeholder, PlaceholderContext context) {
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] Attempting to replace placeholder: " + placeholder + " " + context);
        Function<PlaceholderContext, TextComponent> valueFunction = placeholders.get(placeholder.toLowerCase());
        if (valueFunction != null) {
            try {
                plugin.getLogger().spam("[ShopMessage.replacePlaceholder]     Running placeholder function... " + placeholder);
                TextComponent message = valueFunction.apply(context);
                if (message != null) {
                    plugin.getLogger().trace("[ShopMessage.replacePlaceholder]  *** placeholder " + placeholder + "  value: " + message);
                    return message;
                }
            } catch (Exception e) {
                // Log the exception
                Bukkit.getLogger().warning("Error replacing placeholder [" + placeholder + "]: " + e.getMessage());
            }
        }
        // If placeholder not found, remove the placeholder and just return an empty string
        plugin.getLogger().spam("[ShopMessage.replacePlaceholder] *** returning empty string, unable to get function to replace placeholder: " + placeholder);
        return new TextComponent("");
    }

    /**
     * Formats a message by replacing all placeholders with their respective values.
     *
     * @param message The message containing placeholders
     * @param context The PlaceholderContext instance containing Shop and Player
     * @return The formatted message with all placeholders replaced
     */
    public static TextComponent format(String message, PlaceholderContext context) {
        plugin.getLogger().debug("[ShopMessage] pre-format: " + ChatColor.translateAlternateColorCodes('&', message), true);
        TextComponent formattedMessage = new TextComponent("");

        // Define the regex pattern
//        MESSAGE_PARTS_REGEX
        Matcher matcher = Pattern.compile(MESSAGE_PARTS_REGEX).matcher(message);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group());
        }

        ChatColor latestColor = null;
        for (String part : parts) {
            plugin.getLogger().spam("\n\n");
            plugin.getLogger().trace("[ShopMessage.format] part: " + part);
            TextComponent partComponent = new TextComponent(part);

            // Check if we are a color code
            if (part.matches(COLOR_CODE_REGEX) || part.matches(HEX_CODE_REGEX)) {
                try {
                    ChatColor newColor = null;
                    if (part.matches(HEX_CODE_REGEX)) { newColor = ChatColor.of(part); }
                    else if (part.matches(COLOR_CODE_REGEX)) {
                        newColor = ChatColor.getByChar(part.charAt(1));
                    }
                    if (newColor != null) {
                        latestColor = newColor;
                    }
                    plugin.getLogger().spam("[ShopMessage.format]     matched COLOR_CODE_REGEX: " + part);
                    plugin.getLogger().spam("[ShopMessage.format]     newColor: " + newColor.toString());
                    plugin.getLogger().spam("[ShopMessage.format] *** skipping to next part: " + newColor.getName().toUpperCase());
                    continue; // Don't add this text to the message, just go to the next part
                } catch (Exception e) {
                    plugin.getLogger().spam("[ShopMessage.format] XXX unknown color code! Going to add this as a normal string! " + part);
                }
            }

            // If we match to a placeholder, then we want to use it's TextComponent instead of the "normal" one
            if (part.matches(PLACEHOLDER_REGEX)) {
                plugin.getLogger().hyper("[ShopMessage.format]     matched PLACEHOLDER_REGEX: " + part);
                plugin.getLogger().spam("[ShopMessage.format]     is part placeholder? " + (placeholders.get(part) != null));
                if (placeholders.get(part) != null) {
                    plugin.getLogger().spam("[ShopMessage.format]     replacing placeholder... " + part);
                    partComponent = replacePlaceholder(part, context);
                    // Check if we set a color inside our part (for example [stock color])
                    if (partComponent.getColor() != latestColor && partComponent.getColor() != null && partComponent.getColor() != ChatColor.WHITE) { latestColor = partComponent.getColor(); }
                }
            }

            // Set the color
            if (latestColor != null) {
                plugin.getLogger().spam("[ShopMessage.format]     setting part color to: " + latestColor.getName().toUpperCase());
                partComponent.setColor(latestColor);
            }
            // Add the part of the string to the
            formattedMessage.addExtra(partComponent);
            plugin.getLogger().spam("[ShopMessage.format] *** add part TextComponent to main message: " + partComponent);
        }

        plugin.getLogger().debug("[ShopMessage] postFormat: " + formattedMessage.toLegacyText(), true);
        return formattedMessage;
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, PlaceholderContext context) {
        TextComponent fancyMessage = format(message, context);
        player.spigot().sendMessage(fancyMessage);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, ItemStack item) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setItem(item);
        sendMessage(message, player, context);
    }

    /**
     * Loads message, swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String key, String subkey, Player player, AbstractShop shop) {
        String message = getUnformattedMessage(key, subkey);
        if(message != null && !message.isEmpty())
            sendMessage(message, player, shop);
    }

    /**
     * Loads message, swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String key, String subkey, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        String message = getUnformattedMessage(key, subkey);
        if(message != null && !message.isEmpty())
            sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, Player player, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setShop(shop);
        sendMessage(message, player, context);
    }

    /**
     * Swaps in placeholder values, sends fancy message with Click/Hover events to Player
     */
    public static void sendMessage(String message, ShopCreationProcess process, Player player) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setProcess(process);
        sendMessage(message, player, context);
    }

    /**
     * Loads all available placeholders into the map.
     * This method should be called during the plugin's initialization phase.
     */
    public static void loadPlaceholders() {
        registerPlaceholder("[plugin]", context -> new TextComponent(plugin.getCommandAlias()));
        registerPlaceholder("[server name]", context -> new TextComponent(ShopMessage.getServerDisplayName()));
        registerPlaceholder("[player]", context -> { Player player = context.getPlayer(); return new TextComponent((player != null) ? player.getName() : ""); });
        registerPlaceholder("[user]", context -> new TextComponent(context.getPlayer().getName()));
        registerPlaceholder("[shop type]", context -> {
            if (context.getProcess() != null && context.getProcess().getShopType() != null) return new TextComponent(context.getProcess().getShopType().toString());
            return new TextComponent(ShopMessage.getCreationWord(context.getShop().getType().toString().toUpperCase()));
        });
        registerPlaceholder("[shop types]", ShopMessage::getShopTypesPlaceholder);
        registerPlaceholder("[total shops]", context -> new TextComponent(String.valueOf(plugin.getShopHandler().getNumberOfShops())));

        // Player Info Placeholders
        registerPlaceholder("[owner]", context -> {
            if (context.getProcess() != null) return new TextComponent(String.valueOf(Bukkit.getOfflinePlayer(context.getProcess().getPlayerUUID())));
            else if (context.getShop() != null) return new TextComponent(context.getShop().isAdmin() ? ShopMessage.getServerDisplayName() : context.getShop().getOwnerName());
            return null;
        });
        registerPlaceholder("[user amount]", context -> {
            if (context.getPlayer() != null) return new TextComponent(String.valueOf(plugin.getShopHandler().getNumberOfShops(context.getPlayer())));
            else if (context.getShop().getOwner() != null) return new TextComponent(String.valueOf(plugin.getShopHandler().getNumberOfShops(context.getShop().getOwner().getUniqueId())));
            return new TextComponent("0"); // If they don't have any shops, it should be 0
        });
        registerPlaceholder("[build limit]", context -> new TextComponent(String.valueOf(plugin.getShopListener().getBuildLimit(context.getPlayer()))));
        registerPlaceholder("[tp time remaining]", context -> new TextComponent(String.valueOf(plugin.getShopListener().getTeleportCooldownRemaining(context.getPlayer()))));

        // Location Placeholders
        registerPlaceholder("[world]", context -> {
            if (context.getProcess() != null) return new TextComponent(context.getProcess().getClickedChest().getWorld().getName());
            else if (context.getShop() != null) new TextComponent(context.getShop().getSignLocation().getWorld().getName());
            return null;
        });
        registerPlaceholder("[location]", context -> {
            Location loc = null;
            if (context.getProcess() != null) loc = context.getProcess().getClickedChest().getLocation();
            else if (context.getShop() != null) loc = context.getShop().getSignLocation();
            if (loc == null) return null;
            return new TextComponent(UtilMethods.getCleanLocation(loc, false));
        });

        // Currency Placeholders
        registerPlaceholder("[currency name]", context -> new TextComponent(plugin.getCurrencyName()));
        registerPlaceholder("[currency item]", context -> embedItem(plugin.getItemNameUtil().getName(plugin.getItemCurrency()), plugin.getItemCurrency()));

        // Shop Item placeholders
        registerPlaceholder("[item]", ShopMessage::getItemPlaceholder);
        registerPlaceholder("[item amount]", context -> {
            if (context.getProcess() != null) return new TextComponent(String.valueOf(context.getProcess().getItemAmount()));
            return new TextComponent(String.valueOf(context.getShop().getItemStack().getAmount()));
        });
        registerPlaceholder("[item enchants]", context -> embedItem(UtilMethods.getEnchantmentsString(context.getShop().getItemStack()), context.getShop().getItemStack()));

        registerPlaceholder("[item lore]", context -> embedItem(UtilMethods.getLoreString(context.getShop().getItemStack()), context.getShop().getItemStack()));
        registerPlaceholder("[item durability]", context -> new TextComponent(String.valueOf(context.getShop().getItemDurabilityPercent())));
        registerPlaceholder("[item type]", context -> { if (context.getShop().getType() == ShopType.GAMBLE) { return new TextComponent("???"); } else { return new TextComponent(Shop.getPlugin().getItemNameUtil().getName(context.getShop().getItemStack().getType())); } });
        registerPlaceholder("[gamble item amount]", context -> { if (context.getShop().getType() == ShopType.GAMBLE) { return new TextComponent(String.valueOf(context.getShop().getAmount())); } return null; });
        registerPlaceholder("[gamble item]", context -> { if (context.getShop().getType() == ShopType.GAMBLE) { return embedItem(plugin.getItemNameUtil().getName(plugin.getGambleDisplayItem()), plugin.getGambleDisplayItem()); } return null; });

        // Shop Barter Item Placeholders
        registerPlaceholder("[barter item amount]", context -> {
            if (context.getProcess() != null) return new TextComponent(String.valueOf(context.getProcess().getBarterItemAmount()));
            return new TextComponent(String.valueOf(context.getShop().getSecondaryItemStack().getAmount()));
        });
        registerPlaceholder("[barter item]", ShopMessage::getBarterItemPlaceholder);
        registerPlaceholder("[barter item durability]", context -> { if (context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return new TextComponent(String.valueOf(context.getShop().getSecondaryItemDurabilityPercent())); } return null; });
        registerPlaceholder("[barter item type]", context -> { if (context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return new TextComponent(Shop.getPlugin().getItemNameUtil().getName(context.getShop().getSecondaryItemStack().getType())); } return null; });
        registerPlaceholder("[barter item enchants]", context -> { if (context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return embedItem(UtilMethods.getEnchantmentsString(context.getShop().getSecondaryItemStack()), context.getShop().getSecondaryItemStack()); } return null; });
        registerPlaceholder("[barter item lore]", context -> { if (context.getShop().getType() == ShopType.BARTER && context.getShop().getSecondaryItemStack() != null) { return embedItem(UtilMethods.getLoreString(context.getShop().getSecondaryItemStack()), context.getShop().getSecondaryItemStack()); } return null; });

        // Shop Pricing Placeholders
        registerPlaceholder("[amount]", context -> new TextComponent(String.valueOf(context.getShop().getAmount())));
        registerPlaceholder("[price sell]", context -> { if (context.getShop().getType() == ShopType.COMBO) { return new TextComponent(((ComboShop) context.getShop()).getPriceSellString()); } return null; });
        registerPlaceholder("[price sell per item]", context -> { if (context.getShop().getType() == ShopType.COMBO) { return new TextComponent(((ComboShop) context.getShop()).getPriceSellPerItemString()); } return null; });
        registerPlaceholder("[price combo]", context -> { if (context.getShop().getType() == ShopType.COMBO) { return new TextComponent(((ComboShop) context.getShop()).getPriceComboString()); } return null; });
        registerPlaceholder("[price per item]", context -> new TextComponent(context.getShop().getPricePerItemString()));
        registerPlaceholder("[price]", context -> new TextComponent(context.getShop().getPriceString()));
        registerPlaceholder("[stock]", context -> {
            if (context.getShop().isAdmin()) {
                return new TextComponent(String.valueOf(ShopMessage.getAdminStockWord()));
            } else {
                // This also sets sign lines to require refresh
                // @TODO: Why do they require a refresh...?
                context.getShop().setSignLinesRequireRefresh(true);
                return new TextComponent(String.valueOf(context.getShop().getStock()));
            }
        });
        registerPlaceholder("[stock color]", context -> {
            // This also sets sign lines to require refresh
            // @TODO: Why do they require a refresh...?
            context.getShop().setSignLinesRequireRefresh(true);
            TextComponent component = new TextComponent("");
            component.setColor((context.getShop().getStock() > 0) ? ChatColor.GREEN : ChatColor.DARK_RED);
            return component;
        });

        // Notify Placeholders
        registerPlaceholder("[notify user]", context -> {
            // @TODO: is this correct?
            String text_on = getUnformattedMessage("command", "notify_on");
            String text_off = getUnformattedMessage("command", "notify_off");

            ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(context.getPlayer(), PlayerSettings.Option.NOTIFICATION_SALE_USER);
            return new TextComponent((guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_USER_ON) ? text_on : text_off);
        });

        registerPlaceholder("[notify owner]", context -> {
            String text_on = getUnformattedMessage("command", "notify_on");
            String text_off = getUnformattedMessage("command", "notify_off");

            ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(context.getPlayer(), PlayerSettings.Option.NOTIFICATION_SALE_OWNER);
            return new TextComponent((guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_OWNER_ON) ? text_on : text_off);
        });

        registerPlaceholder("[notify stock]", context -> {
            String text_on = getUnformattedMessage("command", "notify_on");
            String text_off = getUnformattedMessage("command", "notify_off");

            ShopGuiHandler.GuiIcon guiIcon = plugin.getGuiHandler().getIconFromOption(context.getPlayer(), PlayerSettings.Option.NOTIFICATION_STOCK);
            return new TextComponent((guiIcon == ShopGuiHandler.GuiIcon.SETTINGS_NOTIFY_STOCK_ON) ? text_on : text_off);
        });
    }

    private static HoverEvent getItemHoverEvent(ItemStack item) {
        try {
            ItemStack hoverItem = item.clone();
            hoverItem.setAmount(1);
            BaseComponent[] component = new BaseComponent[]{new TextComponent(NBT.itemStackToNBT(hoverItem).toString())};
            return new HoverEvent(HoverEvent.Action.SHOW_ITEM, component);
        } catch (Exception e) {}
        return null;
    }

    private static TextComponent embedItem(String message, ItemStack item) {
        TextComponent msg = new TextComponent(message);
        HoverEvent event = getItemHoverEvent(item);
        if (event != null) { msg.setHoverEvent(event); }
        return msg;
    }


    /**
     * Helper method to handle the [shop types] placeholder.
     *
     * @param context The PlaceholderContext instance.
     * @return A comma-separated list of shop types the player can create.
     */
    private static TextComponent getShopTypesPlaceholder(PlaceholderContext context) {
        List<ShopType> typeList = new ArrayList<>(Arrays.asList(ShopType.values()));
        Player player = context.getPlayer();

        if ((!plugin.usePerms() && !player.isOp()) || (plugin.usePerms() && !player.hasPermission("shop.operator"))) {
            typeList.remove(ShopType.GAMBLE);
        }

        if (plugin.usePerms()) {
            Iterator<ShopType> typeIterator = typeList.iterator();
            while (typeIterator.hasNext()) {
                ShopType type = typeIterator.next();
                if (!player.hasPermission("shop.operator") && !player.hasPermission("shop.create." + type.toString())) {
                    typeIterator.remove();
                }
            }
        }

        StringBuilder types = new StringBuilder();
        for (int i = 0; i < typeList.size(); i++) {
            types.append(typeList.get(i).toCreationWord());
            if (i < typeList.size() - 1) {
                types.append(", ");
            }
        }
        return new TextComponent(types.toString());
    }

    /**
     * Helper method to handle the [item] placeholder with truncation for signs.
     *
     * @param context The PlaceholderContext instance.
     * @return The item name, potentially truncated to fit sign constraints.
     */
    private static TextComponent getItemPlaceholder(PlaceholderContext context) {
        ItemStack item = null;
        if (context.getItem() != null) {
            item = context.getItem();
        }
        else if (context.getProcess() != null) {
            item = context.getProcess().getItemStack(); 
        }
        else if (context.getShop() != null || context.getShop().getItemStack() != null) {
            item = context.getShop().getItemStack();
        }
        if (item == null) { return null; }

        String itemName = plugin.getItemNameUtil().getName(item);
        if (context.isForSign()) {
            return new TextComponent(UtilMethods.trimForSign(itemName));
        }
        return embedItem(itemName, item);
    }

    /**
     * Helper method to handle the [barter item] placeholder with truncation for signs.
     *
     * @param context The PlaceholderContext instance.
     * @return The barter item name, potentially truncated to fit sign constraints.
     */
    private static TextComponent getBarterItemPlaceholder(PlaceholderContext context) {
        ItemStack item = null;
        if (context.getItem() != null) {
            item = context.getItem();
        }
        else if (context.getProcess() != null) {
            item = context.getProcess().getBarterItemStack();
        }
        else if (context.getShop() != null && context.getShop().getSecondaryItemStack() != null) {
            if (context.getShop().getType() != ShopType.BARTER) {
                return null;
            }
            item = context.getShop().getSecondaryItemStack();
        }
        if (item == null) { return null; }

        String itemName = plugin.getItemNameUtil().getName(item);
        if (context.isForSign()) {
            return new TextComponent(UtilMethods.trimForSign(itemName));
        }
        return embedItem(itemName, item);
    }

    public static String getCreationWord(String type) {
        return creationWords.get(type);
    }

    public static String getFreePriceWord() {
        return freePriceWord;
    }

    public static String getAdminStockWord() {
        return adminStockWord;
    }

    public static String getServerDisplayName() {
        return serverDisplayName;
    }

    public static String getUnformattedMessage(String key, String subKey) {
        String message;
        if (subKey != null)
            message = messageMap.get(key + "_" + subKey);
        else
            message = messageMap.get(key);
        return message;
    }

    // Embeds hover objects for items!
    public static void embedAndSendHoverItemsMessage(String message, Player player, Map<ItemStack, Integer> items){
        if(message == null)
            return;

        String[] parts = message.replace("§", "&").split("(?=&[0-9A-FK-ORa-fk-or])");
        TextComponent fancyMessage = new TextComponent("");

        for(String part : parts){
            ComponentBuilder builder = new ComponentBuilder("");
            net.md_5.bungee.api.ChatColor cc = UtilMethods.getChatColor(part);
            if(cc != null)
                part = part.substring(2, part.length());
            builder.append(part);
            if(cc != null) {
                builder.color(ChatColor.valueOf(cc.name()));
            }

            ItemStack item = null;
            for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
                if (part.contains(Shop.getPlugin().getItemNameUtil().getName(entry.getKey()))) {
                    item = entry.getKey();
                }
            }

            try {
                if (item != null) {
                    // Clear out the item aount
                    ItemStack hoverItem = item.clone();
                    hoverItem.setAmount(1); // Force it to be a single unit just in case we were selling a large quantity greater than a single stack, otherwise this will throw an error!
                    BaseComponent[] hoverEventComponents = new BaseComponent[]{new TextComponent(NBT.itemStackToNBT(hoverItem).toString())}; // The only element of the hover events basecomponents is the item json
                    HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_ITEM, hoverEventComponents);
                    builder.event(event);
                }
            } catch (Exception e) {
                // Some sort of error occured, likely with the NBT api, just don't embed the hover event
                // For now, log an error
                Shop.getPlugin().getLogger().log(Level.WARNING, "Unable to generate Item Hover, there is likely an issue with NBT data! Item: " + item.serialize());
            }

            for(BaseComponent b : builder.create()) {
                fancyMessage.addExtra(b);
            }
        }
        player.spigot().sendMessage(fancyMessage);
    }

    public static String formatMessage(String unformattedMessage, Player player, OfflineTransactions offlineTransactions) {
        if (offlineTransactions != null && player != null) {
            // Replace basic placeholders
            unformattedMessage = unformattedMessage.replace("[offline transactions]", String.valueOf(offlineTransactions.getNumTransactions()));
            String boughtString = Shop.getPlugin().getPriceString(offlineTransactions.getTotalProfit(), false);
            if (boughtString.equals("FREE")) { boughtString = "0"; }
            unformattedMessage = unformattedMessage.replace("[offline profit]", boughtString);
            String spentString = Shop.getPlugin().getPriceString(offlineTransactions.getTotalSpent(), false);
            if (spentString.equals("FREE")) { spentString = "0"; }
            unformattedMessage = unformattedMessage.replace("[offline spent]", spentString);

            List<AbstractShop> playerShops = Shop.getPlugin().getShopHandler().getShops(player.getUniqueId());

            // Process items sold
            unformattedMessage = processItemRows(
                    unformattedMessage,
                    "[item name sold row]",
                    "[item amount sold row]",
                    offlineTransactions.getItemsSold()
            );

            // Process items bought
            unformattedMessage = processItemRows(
                    unformattedMessage,
                    "[item name bought row]",
                    "[item amount bought row]",
                    offlineTransactions.getItemsBought()
            );

            // Process shops out of stock
            unformattedMessage = processShopStockRows(
                    unformattedMessage,
                    "[out of stock item]",
                    "[out of stock location]",
                    playerShops
            );
        }

        return unformattedMessage;
    }

    private static String addRowsDualPlaceholder(
            String message,
            String placeholderOne,
            String placeholderTwo,
            Map<String, String> rowData
    ) {
        // Split the message into lines
        String[] lines = message.split("\\r?\\n");
        StringBuilder newMessage = new StringBuilder();

        for (String line : lines) {
            boolean foundLine = line.contains(placeholderOne);
            if (placeholderTwo != null) { foundLine = foundLine && line.contains(placeholderTwo); };
            // Does this line contain the placeholders we are looking for?
            if (foundLine) {
                if (rowData != null && !rowData.isEmpty()) {
                    // For each item, generate a line based on the template line
                    for (Map.Entry<String, String> entry : rowData.entrySet()) {
                        String itemLine = line;
                        itemLine = itemLine.replace(placeholderOne, entry.getKey());
                        if (placeholderTwo != null) { itemLine = itemLine.replace(placeholderTwo, entry.getValue()); }
                        newMessage.append(itemLine).append("\n");
                    }
                } else {
                    // No objects were sent to us to log, just log nothing (remove line with placeholders)
                    // If we want to log something like "No Items" in the future we could do that here.
                }
            } else {
                // This line didn't contain the placeholders, add it and continue
                newMessage.append(line).append("\n");
            }
        }

        return newMessage.toString().trim();
    }

    private static String processItemRows(
            String message,
            String itemNamePlaceholder,
            String itemAmountPlaceholder,
            Map<ItemStack, Integer> items
    ) {
        Map<String, String> rowData = new HashMap<>();
        for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
            String itemName = Shop.getPlugin().getItemNameUtil().getName(entry.getKey());
            String itemAmount = String.valueOf(entry.getValue());
            rowData.put(itemName, itemAmount);
        }

        return addRowsDualPlaceholder(message, itemNamePlaceholder, itemAmountPlaceholder, rowData);
    }

    private static String processShopStockRows(
            String message,
            String shopItemPlaceholder,
            String locationPlaceholder,
            List<AbstractShop> shops
    ) {
        // Split the message into lines
        String[] lines = message.split("\\r?\\n");
        StringBuilder newMessage = new StringBuilder();

        for (String line : lines) {
            if (line.contains(shopItemPlaceholder) && line.contains(locationPlaceholder)) {
                if (shops != null && !shops.isEmpty()) {
                    // For each item, generate a line based on the template line
                    int outOfStockShops = 0;
                    int remainingOutOfStock = 0;
                    for (AbstractShop shop : shops) {
                        if (shop.getStock() > 0) { continue; }
                        outOfStockShops++;
                        if (outOfStockShops > 3) {
                            remainingOutOfStock++;
                            continue;
                        }
                        String itemLine = line;
                        itemLine = itemLine.replace(shopItemPlaceholder, Shop.getPlugin().getItemNameUtil().getName(shop.getItemStack()));
                        Location loc = shop.getChestLocation();
                        itemLine = itemLine.replace(locationPlaceholder, "(" + loc.getBlockX() + "," + loc.getBlockY() + ","+ loc.getBlockZ() + ")");
                        newMessage.append(itemLine).append("\n");
                    }

                    if (remainingOutOfStock > 0) {
                        String remainingMsg = messageMap.get("too_many_out_of_stock");
                        newMessage.append(remainingMsg.replace("[out of stock remaining]", "" + remainingOutOfStock)).append("\n");
                    }
                } else {
                    // No items, don't log anything
                }
            } else {
                newMessage.append(line).append("\n");
            }
        }

        return newMessage.toString().trim();
    }

    public static String formatMessage(String unformattedMessage, AbstractShop shop) {
        PlaceholderContext context = new PlaceholderContext();
        context.setShop(shop);
        TextComponent formattedMessage = format(unformattedMessage, context);
        // Return the legacy version since we are requesting the legacy formatter!
        return ChatColor.translateAlternateColorCodes('§', formattedMessage.toLegacyText());
    }

    public static String formatMessage(String unformattedMessage, AbstractShop shop, Player player, boolean forSign) {
        PlaceholderContext context = new PlaceholderContext();
        context.setPlayer(player);
        context.setShop(shop);
        context.setForSign(forSign);
        TextComponent formattedMessage = format(unformattedMessage, context);
        // Return the legacy version since we are requesting the legacy formatter!
        return ChatColor.translateAlternateColorCodes('§', formattedMessage.toLegacyText());
    }

    //      # [amount] : The amount of items the shop is selling/buying/bartering #
    //      # [price] : The price of the items the shop is selling (adjusted to match virtual or physical currency) #
    //      # [owner] : The name of the shop owner #
    //      # [server name] : The name of the server #
    public static String[] getSignLines(AbstractShop shop, ShopType shopType){

        DisplayType displayType = shop.getDisplay().getType();
        if(displayType == null)
            displayType = Shop.getPlugin().getDisplayType();

        String shopFormat;
        if(shop.isAdmin())
            shopFormat = "admin";
        else
            shopFormat = "normal";

        if(displayType == DisplayType.NONE){
            shopFormat += "_no_display";
        }

        String[] lines = getUnformattedShopSignLines(shopType, shopFormat);

        for(int i=0; i<lines.length; i++) {
            lines[i] = formatMessage(lines[i], shop, null, true);
            lines[i] = ChatColor.translateAlternateColorCodes('&', lines[i]);

            //TODO have smart way of cutting lines if too long so at least some of word can go on
//            if(lines[i].length() > 15)
//                lines[i]
        }
        return lines;
    }

    public static String[] getTimeoutSignLines(AbstractShop shop){

        String[] lines = shopSignTextMap.get("timeout");

        for(int i=0; i<lines.length; i++) {
            lines[i] = formatMessage(lines[i], shop, null, true);
            lines[i] = ChatColor.translateAlternateColorCodes('&', lines[i]);

            //TODO have smart way of cutting lines if too long so at least some of word can go on
//            if(lines[i].length() > 15)
//                lines[i]
        }
        return lines;
    }

    public static ArrayList<String> getDisplayTags(AbstractShop shop, ShopType shopType){

        //in future may need to add more options here like "admin" or "no stock" or "no display" other than normal

//        String shopFormat;
//        if(shop.isAdmin())
//            shopFormat = "admin";
//        else
            String shopFormat = "normal";

//        if(displayType == DisplayType.NONE){
//            shopFormat += "_no_display";
//        }

        ArrayList<String> formattedLines = new ArrayList<>();
        List<String> lines = displayTextMap.get(shopType.toString().toUpperCase()+"_"+shopFormat);

        String formattedLine;
        for(String line : lines) {
            formattedLine = formatMessage(line, shop, null, false);
            formattedLine = ChatColor.translateAlternateColorCodes('&', formattedLine);

            if(!formattedLine.isEmpty() && !ChatColor.stripColor(formattedLine).isEmpty() && (ChatColor.stripColor(formattedLine).trim().length() > 0))
                formattedLines.add(formattedLine);
        }
        return formattedLines;
    }

    public static List<String> getUnformattedMessageList(String key, String subKey){
        List<String> messages = new ArrayList<>();

        int count = 1;
        String message = "-1";
        while (message != null && !message.isEmpty()) {
            message = getUnformattedMessage(key, subKey + count);
            if (message != null && !message.isEmpty())
                messages.add(message);
            count++;
        }
        return messages;
    }

    private static String[] getUnformattedShopSignLines(ShopType type, String subtype) {
        return shopSignTextMap.get(type.toString()+"_"+subtype).clone();
    }

    private static void loadMessagesFromConfig() {

        for (ShopType type : ShopType.values()) {
            messageMap.put(type.toString() + "_user", chatConfig.getString("transaction." + type.toString().toUpperCase() + ".user"));
            messageMap.put(type.toString() + "_owner", chatConfig.getString("transaction." + type.toString().toUpperCase() + ".owner"));

            messageMap.put(type.toString() + "_initialize", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initialize"));
            if(type == ShopType.BUY || type == ShopType.COMBO)
                messageMap.put(type.toString() + "_initializeAlt", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeAlt"));
            else if(type == ShopType.BARTER) {
                messageMap.put(type.toString() + "_initializeInfo", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeInfo"));
                messageMap.put(type.toString() + "_initializeBarter", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeBarter"));
                messageMap.put(type.toString() + "_createHitChest", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChest"));
                messageMap.put(type.toString() + "_createHitChestBarterAmount", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestBarterAmount"));
                messageMap.put(type.toString() + "_initializeBarterAlt", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".initializeBarterAlt"));
            }
            messageMap.put(type.toString() + "_create", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".create"));
            messageMap.put(type.toString() + "_destroy", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".destroy"));
            messageMap.put(type.toString() + "_opDestroy", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".opDestroy"));
            messageMap.put(type.toString() + "_opOpen", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".opOpen"));

            messageMap.put(type.toString() + "_shopNoStock", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".shopNoStock"));
            messageMap.put(type.toString() + "_ownerNoStock", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".ownerNoStock"));
            messageMap.put(type.toString() + "_shopNoSpace", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".shopNoSpace"));
            messageMap.put(type.toString() + "_ownerNoSpace", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".ownerNoSpace"));
            messageMap.put(type.toString() + "_playerNoStock", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".playerNoStock"));
            messageMap.put(type.toString() + "_playerNoSpace", chatConfig.getString("transaction_issue." + type.toString().toUpperCase() + ".playerNoSpace"));

            if(type != ShopType.GAMBLE){
                messageMap.put(type.toString() + "_createHitChestAmount", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestAmount"));
            }
            if(type != ShopType.BARTER){
                messageMap.put(type.toString() + "_createHitChestPrice", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestPrice"));
            }
            if(type == ShopType.COMBO){
                messageMap.put(type.toString() + "_createHitChestPriceCombo", chatConfig.getString("interaction." + type.toString().toUpperCase() + ".createHitChestPriceCombo"));
            }

            int count = 1;
            for(String s : chatConfig.getStringList("description."+type.toString().toUpperCase())){
                messageMap.put(type.toString() + "_description"+count, s);
                count++;
            }
        }
        messageMap.put("createHitChest", chatConfig.getString("interaction.createHitChest"));
        messageMap.put("adminCreateHitChest", chatConfig.getString("interaction.adminCreateHitChest"));

        messageMap.put("permission_use", chatConfig.getString("permission.use"));
        messageMap.put("permission_create", chatConfig.getString("permission.create"));
        messageMap.put("permission_destroy", chatConfig.getString("permission.destroy"));
        messageMap.put("permission_buildLimit", chatConfig.getString("permission.buildLimit"));

        messageMap.put("interactionIssue_line2", chatConfig.getString("interaction_issue.createLine2"));
        messageMap.put("interactionIssue_line3", chatConfig.getString("interaction_issue.createLine3"));
        messageMap.put("interactionIssue_noItem", chatConfig.getString("interaction_issue.createNoItem"));
        messageMap.put("interactionIssue_direction", chatConfig.getString("interaction_issue.createDirection"));
        messageMap.put("interactionIssue_sameItem", chatConfig.getString("interaction_issue.createSameItem"));
        messageMap.put("interactionIssue_displayRoom", chatConfig.getString("interaction_issue.createDisplayRoom"));
        messageMap.put("interactionIssue_signRoom", chatConfig.getString("interaction_issue.createSignRoom"));
        messageMap.put("interactionIssue_createOtherPlayer", chatConfig.getString("interaction_issue.createOtherShop"));
        messageMap.put("interactionIssue_createInsufficientFunds", chatConfig.getString("interaction_issue.createInsufficientFunds"));
        messageMap.put("interactionIssue_createCooldown", chatConfig.getString("interaction_issue.createCooldown"));
        messageMap.put("interactionIssue_destroyInsufficientFunds", chatConfig.getString("interaction_issue.destroyInsufficientFunds"));
        messageMap.put("interactionIssue_createCancel", chatConfig.getString("interaction_issue.createCancel"));
        messageMap.put("interactionIssue_teleportInsufficientFunds", chatConfig.getString("interaction_issue.teleportInsufficientFunds"));
        messageMap.put("interactionIssue_teleportInsufficientCooldown", chatConfig.getString("interaction_issue.teleportInsufficientCooldown"));
        messageMap.put("interactionIssue_initialize", chatConfig.getString("interaction_issue.initializeOtherShop"));
        messageMap.put("interactionIssue_destroyChest", chatConfig.getString("interaction_issue.destroyChest"));
        messageMap.put("interactionIssue_useOwnShop", chatConfig.getString("interaction_issue.useOwnShop"));
        messageMap.put("interactionIssue_useShopAlreadyInUse", chatConfig.getString("interaction_issue.useShopAlreadyInUse"));
        messageMap.put("interactionIssue_adminOpen", chatConfig.getString("interaction_issue.adminOpen"));
        messageMap.put("interactionIssue_worldBlacklist", chatConfig.getString("interaction_issue.worldBlacklist"));
        messageMap.put("interactionIssue_regionRestriction", chatConfig.getString("interaction_issue.regionRestriction"));
        messageMap.put("interactionIssue_itemListDeny", chatConfig.getString("interaction_issue.itemListDeny"));
        messageMap.put("interactionIssue_createHitChestTimeout", chatConfig.getString("interaction_issue.createHitChestTimeout"));


        int count = 1;
        for(String s : chatConfig.getStringList("creativeSelection.enter")){
            messageMap.put("creativeSelection_enter"+count, s);
            count++;
        }
        count = 1;
        for(String s : chatConfig.getStringList("creativeSelection.prompt")){
            messageMap.put("creativeSelection_prompt"+count, s);
            count++;
        }

        count = 1;
        for(String s : chatConfig.getStringList("guiSearchSelection.enter")){
            messageMap.put("guiSearchSelection_enter"+count, s);
            count++;
        }
        count = 1;
        for(String s : chatConfig.getStringList("guiSearchSelection.prompt")){
            messageMap.put("guiSearchSelection_prompt"+count, s);
            count++;
        }

        count = 1;
        for(String s : chatConfig.getStringList("transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.summary")){
            messageMap.put("offline_transactions"+count, s);
            count++;
        }
        messageMap.put("too_many_out_of_stock", chatConfig.getString("transaction.OFFLINE_TRANSACTIONS_NOTIFICATION.tooManyOutOfStock"));

        messageMap.put("command_list", chatConfig.getString("command.list"));
        messageMap.put("command_list_output_total", chatConfig.getString("command.list_output_total"));
        messageMap.put("command_list_output_perms", chatConfig.getString("command.list_output_perms"));
        messageMap.put("command_list_output_noperms", chatConfig.getString("command.list_output_noperms"));
        messageMap.put("command_currency", chatConfig.getString("command.currency"));
        messageMap.put("command_currency_output", chatConfig.getString("command.currency_output"));
        messageMap.put("command_currency_output_tip", chatConfig.getString("command.currency_output_tip"));
        messageMap.put("command_setcurrency", chatConfig.getString("command.setcurrency"));
        messageMap.put("command_setcurrency_output", chatConfig.getString("command.setcurrency_output"));
        messageMap.put("command_setgamble", chatConfig.getString("command.setgamble"));
        messageMap.put("command_itemrefresh", chatConfig.getString("command.itemrefresh"));
        messageMap.put("command_itemrefresh_output", chatConfig.getString("command.itemrefresh_output"));
        messageMap.put("command_itemlist", chatConfig.getString("command.itemlist"));
        messageMap.put("command_itemlist_add", chatConfig.getString("command.itemlist_add"));
        messageMap.put("command_itemlist_remove", chatConfig.getString("command.itemlist_remove"));
        messageMap.put("command_reload", chatConfig.getString("command.reload"));
        messageMap.put("command_reload_output", chatConfig.getString("command.reload_output"));
        messageMap.put("command_error_novault", chatConfig.getString("command.error_novault"));
        messageMap.put("command_error_nohand", chatConfig.getString("command.error_nohand"));
        messageMap.put("command_not_authorized", chatConfig.getString("command.not_authorized"));
        messageMap.put("command_notify_user", chatConfig.getString("command.notify_user"));
        messageMap.put("command_notify_owner", chatConfig.getString("command.notify_owner"));
        messageMap.put("command_notify_stock", chatConfig.getString("command.notify_stock"));
        messageMap.put("command_notify_on", chatConfig.getString("command.notify_on"));
        messageMap.put("command_notify_off", chatConfig.getString("command.notify_off"));
    }

    private void loadSignTextFromConfig() {
        Set<String> allTypes = signConfig.getConfigurationSection("sign_text").getKeys(false);
        for (String typeString : allTypes) {

            ShopType type = null;
            try { type = ShopType.valueOf(typeString);}
            catch (IllegalArgumentException e){}

            if (type != null) {
                try {
                    Set<String> normalLineNumbers = signConfig.getConfigurationSection("sign_text." + typeString + ".normal").getKeys(false);
                    String[] normalLines = new String[4];

                    int i = 0;
                    for (String number : normalLineNumbers) {
                        String message = signConfig.getString("sign_text." + typeString + ".normal." + number);
                        if (message == null)
                            normalLines[i] = "";
                        else
                            normalLines[i] = message;
                        i++;
                    }

                    this.shopSignTextMap.put(type.toString() + "_normal", normalLines);
                } catch (NullPointerException e) {}

                try {
                    Set<String> adminLineNumbers = signConfig.getConfigurationSection("sign_text." + typeString + ".admin").getKeys(false);
                    String[] adminLines = new String[4];

                    int i = 0;
                    for (String number : adminLineNumbers) {
                        String message = signConfig.getString("sign_text." + typeString + ".admin." + number);
                        if (message == null)
                            adminLines[i] = "";
                        else
                            adminLines[i] = message;
                        i++;
                    }

                    this.shopSignTextMap.put(type.toString() + "_admin", adminLines);
                } catch (NullPointerException e) {}

                try {
                    Set<String> normalNoDisplayLineNumbers = signConfig.getConfigurationSection("sign_text." + typeString + ".normal_no_display").getKeys(false);
                    String[] normalNoDisplayLines = new String[4];

                    int i = 0;
                    for (String number : normalNoDisplayLineNumbers) {
                        String message = signConfig.getString("sign_text." + typeString + ".normal_no_display." + number);
                        if (message == null)
                            normalNoDisplayLines[i] = "";
                        else
                            normalNoDisplayLines[i] = message;
                        i++;
                    }

                    this.shopSignTextMap.put(type.toString() + "_normal_no_display", normalNoDisplayLines);
                } catch (NullPointerException e) {}

                try {
                    Set<String> adminNoDisplayLineNumbers = signConfig.getConfigurationSection("sign_text." + typeString + ".admin_no_display").getKeys(false);
                    String[] adminNoDisplayLines = new String[4];

                    int i = 0;
                    for (String number : adminNoDisplayLineNumbers) {
                        String message = signConfig.getString("sign_text." + typeString + ".admin_no_display." + number);
                        if (message == null)
                            adminNoDisplayLines[i] = "";
                        else
                            adminNoDisplayLines[i] = message;
                        i++;
                    }

                    this.shopSignTextMap.put(type.toString() + "_admin_no_display", adminNoDisplayLines);
                } catch (NullPointerException e) {}
            }
        }
        String[] timeoutLines = new String[4];

        for (int i=1; i<5; i++) {
            String message = signConfig.getString("sign_text.timeout." + i);
            if (message == null)
                timeoutLines[i] = "";
            else
                timeoutLines[i] = message;
            i++;
        }

        this.shopSignTextMap.put("timeout", timeoutLines);
    }

    private void loadDisplayTextFromConfig() {
        displayTextMap = new HashMap<>();
        Set<String> allTypes = displayConfig.getConfigurationSection("display_tag_text").getKeys(false);
        for (String typeString : allTypes) {

            ShopType type = null;
            try { type = ShopType.valueOf(typeString);}
            catch (IllegalArgumentException e){}

            if (type != null) {
                try {
                    List<String> normalLines = displayConfig.getStringList("display_tag_text." + typeString.toUpperCase() + ".normal");
                    this.displayTextMap.put(type.toString().toUpperCase() + "_normal", normalLines);
                } catch (NullPointerException e) {}
            }
        }
    }

    private void loadCreationWords(){
        String shopString = signConfig.getString("sign_creation.SHOP");
        if(shopString != null)
            creationWords.put("SHOP", shopString.toLowerCase());
        else
            creationWords.put("SHOP", "[shop]");

        String sellString = signConfig.getString("sign_creation.SELL");
        if(sellString != null)
            creationWords.put("SELL", sellString.toLowerCase());
        else
            creationWords.put("SELL", "sell");

        String buyString = signConfig.getString("sign_creation.BUY");
        if(buyString != null)
            creationWords.put("BUY", buyString.toLowerCase());
        else
            creationWords.put("BUY", "buy");

        String barterString = signConfig.getString("sign_creation.BARTER");
        if(barterString != null)
            creationWords.put("BARTER", barterString.toLowerCase());
        else
            creationWords.put("BARTER", "barter");

        String gambleString = signConfig.getString("sign_creation.GAMBLE");
        if(gambleString != null)
            creationWords.put("GAMBLE", gambleString.toLowerCase());
        else
            creationWords.put("BARTER", "barter");

        String adminString = signConfig.getString("sign_creation.ADMIN");
        if(adminString != null)
            creationWords.put("ADMIN", adminString.toLowerCase());
        else
            creationWords.put("ADMIN", "admin");

        String comboString = signConfig.getString("sign_creation.COMBO");
        if(comboString != null)
            creationWords.put("COMBO", comboString.toLowerCase());
        else
            creationWords.put("COMBO", "combo");
    }
}