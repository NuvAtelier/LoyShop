package com.snowgears.shop.handler;

import com.snowgears.shop.AbstractShop;
import com.snowgears.shop.Shop;
import com.snowgears.shop.gui.*;
import com.snowgears.shop.util.PlayerSettings;
import com.snowgears.shop.util.UtilMethods;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.*;

public class ShopGuiHandler {

    public enum GuiIcon {
        MENUBAR_BACK, MENUBAR_SEARCH, MENUBAR_LAST_PAGE, MENUBAR_NEXT_PAGE,
        HOME_LIST_OWN_SHOPS, HOME_LIST_PLAYERS, HOME_SETTINGS, HOME_COMMANDS,
        LIST_SHOP, LIST_PLAYER, LIST_PLAYER_ADMIN,
        SETTINGS_NOTIFY_OWNER_ON, SETTINGS_NOTIFY_OWNER_OFF, SETTINGS_NOTIFY_USER_ON, SETTINGS_NOTIFY_USER_OFF, SETTINGS_NOTIFY_STOCK_ON, SETTINGS_NOTIFY_STOCK_OFF,
        COMMANDS_CURRENCY, COMMANDS_SET_CURRENCY, COMMANDS_SET_GAMBLE, COMMANDS_REFRESH_DISPLAYS, COMMANDS_RELOAD
    }

    public enum GuiTitle {
        HOME, LIST_PLAYERS, SETTINGS, COMMANDS

    }

    public Shop plugin;
    private HashMap<UUID, ShopGuiWindow> playerGuiWindows = new HashMap<>();
    private HashMap<UUID, PlayerSettings> playerSettings = new HashMap<>();

    private HashMap<GuiIcon, ItemStack> guiIcons = new HashMap<>();
    private HashMap<GuiTitle, String> guiWindowTitles = new HashMap<>();

    private HashMap<UUID, ItemStack> playerHeads = new HashMap<>();

    public ShopGuiHandler(Shop instance){
        plugin = instance;
        loadIconsAndTitles();
    }

    public ShopGuiWindow getWindow(Player player){
        if(playerGuiWindows.get(player.getUniqueId()) != null){
            return playerGuiWindows.get(player.getUniqueId());
        }
        HomeWindow window = new HomeWindow(player.getUniqueId());
        playerGuiWindows.put(player.getUniqueId(), window);
        return window;
    }

    public void setWindow(Player player, ShopGuiWindow window){
        playerGuiWindows.put(player.getUniqueId(), window);

        window.open();
    }

    //TODO make this text configurable
    public void reloadPlayerHeadIcon(UUID playerUUID){
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skMeta = (SkullMeta) playerHead.getItemMeta();
        if(!playerUUID.equals(plugin.getShopHandler().getAdminUUID())) {
            System.out.println("[Shop] player was not null. Adding owning player to icon skin");
            if(offlinePlayer == null)
                return;
            skMeta.setOwningPlayer(offlinePlayer);
            skMeta.setDisplayName(offlinePlayer.getName());
        }
        else { //admin UUID thats not an actual player
            System.out.println("[Shop] player was null. Adding admin name");
            skMeta.setDisplayName("Admin");
        }

        playerHead.setItemMeta(skMeta);

        List<String> lore = new ArrayList<>();

        lore.add("Shops: "+Shop.getPlugin().getShopHandler().getShops(playerUUID).size());

        skMeta.setLore(lore);

        playerHead.setItemMeta(skMeta);
        playerHeads.put(playerUUID, playerHead);
    }

    public ItemStack getPlayerHeadIcon(UUID playerUUID){
        System.out.println("[Shop] getting player head icon for "+playerUUID);
        if(playerHeads.containsKey(playerUUID))
            return playerHeads.get(playerUUID);
        //System.out.println("Player heads did not contain the head");
        return new ItemStack(Material.AIR); //TODO change this back to null once you see why the icons arent loaded on addShop()
        //return null;
    }

    //TODO have a change window to type method here that can be called from the button listener to clean things up?


    public boolean getSettingsOption(Player player, PlayerSettings.Option option){
        if(playerSettings.get(player.getUniqueId()) != null){
            PlayerSettings settings = playerSettings.get(player.getUniqueId());
            return settings.getOption(option);
        }

        PlayerSettings settings = PlayerSettings.loadFromFile(player);
        if(settings == null)
            settings = new PlayerSettings(player);

        playerSettings.put(player.getUniqueId(), settings);
        return settings.getOption(option);
    }

    public void toggleSettingsOption(Player player, PlayerSettings.Option option){
        PlayerSettings settings;

        if(playerSettings.get(player.getUniqueId()) != null){
            settings = playerSettings.get(player.getUniqueId());
        }
        else {
            settings = PlayerSettings.loadFromFile(player);
            if (settings == null)
                settings = new PlayerSettings(player);
        }

        settings.setOption(option, !getSettingsOption(player, option)); //this also handles saving to file internally
        playerSettings.put(player.getUniqueId(), settings);
    }

    public ItemStack getIcon(GuiIcon iconEnum, UUID playerUUID, AbstractShop shop){
        if(iconEnum == GuiIcon.LIST_SHOP){
            return shop.getGuiIcon();
        }
        else if(iconEnum == GuiIcon.LIST_PLAYER || iconEnum == GuiIcon.LIST_PLAYER_ADMIN){
            return getPlayerHeadIcon(playerUUID);
        }

        if(guiIcons.containsKey(iconEnum))
            return guiIcons.get(iconEnum);
        return null;
    }

    public String getTitle(GuiTitle title){
        return guiWindowTitles.get(title);
    }

    public String getTitle(ShopGuiWindow window){
        if(window instanceof HomeWindow){
            return guiWindowTitles.get(GuiTitle.HOME);
        }
        else if(window instanceof ListPlayersWindow){
            return guiWindowTitles.get(GuiTitle.LIST_PLAYERS);
        }
        else if(window instanceof PlayerSettingsWindow){
            return guiWindowTitles.get(GuiTitle.SETTINGS);
        }
        else if(window instanceof CommandsWindow){
            return guiWindowTitles.get(GuiTitle.COMMANDS);
        }
        return "Window";
    }

    private void loadIconsAndTitles(){
        File configFile = new File(plugin.getDataFolder(), "guiConfig.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("guiConfig.yml"), configFile);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        //load all titles first
        Set<String> titles = config.getConfigurationSection("titles").getKeys(false);

        for(GuiTitle titleEnum : GuiTitle.values()) {
            String titleString = config.getString("titles."+titleEnum.toString().toLowerCase());
            guiWindowTitles.put(titleEnum, titleString);
        }

        Set<String> icons = config.getConfigurationSection("icons").getKeys(false);

        //load all icons next
        for(GuiIcon iconEnum : GuiIcon.values()) {
            String iconString = iconEnum.toString().toLowerCase();
            String parentKey = iconString.substring(0, iconString.indexOf('_'));
            String childKey = iconString.substring(iconString.indexOf('_')+1);


            String type = config.getString("icons."+parentKey+"."+childKey+".type");
            String name = config.getString("icons."+parentKey+"."+childKey+".name");
            if(name != null)
                name = ChatColor.translateAlternateColorCodes('&', name);

            List<String> loreLines = config.getStringList("icons."+parentKey+"."+childKey+".lore");
            List<String> lore = new ArrayList<>();
            if(loreLines != null) {
                for (String line : loreLines) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }

            ItemStack icon = null;
            if(type != null) {
                icon = new ItemStack(Material.valueOf(type.toUpperCase()));
            }
            else if(childKey.equals("set_gamble")){
                icon = plugin.getGambleDisplayItem().clone();
            }
            else if(parentKey.equals("list")){
                if(childKey.equals("player")) {
                    icon = new ItemStack(Material.PLAYER_HEAD, 1, (short) 3);
                }
            }

            if(icon != null) {
                ItemMeta iconMeta = icon.getItemMeta();

                if (name != null)
                    iconMeta.setDisplayName(name);
                if (lore != null && !lore.isEmpty())
                    iconMeta.setLore(lore);

                icon.setItemMeta(iconMeta);
                guiIcons.put(iconEnum, icon);
            }
        }
    }
}
