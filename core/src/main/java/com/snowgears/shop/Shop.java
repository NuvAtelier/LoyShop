package com.snowgears.shop;

import com.snowgears.shop.display.DisplayTagOption;
import com.snowgears.shop.display.DisplayType;
import com.snowgears.shop.gui.ShopGUIListener;
import com.snowgears.shop.handler.*;
import com.snowgears.shop.hook.*;
import com.snowgears.shop.listener.CreativeSelectionListener;
import com.snowgears.shop.listener.DisplayListener;
import com.snowgears.shop.listener.MiscListener;
import com.snowgears.shop.listener.ShopListener;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.*;
import com.snowgears.shop.util.Metrics;
import com.snowgears.shop.util.Metrics.*;
import java.util.concurrent.Callable;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Shop extends JavaPlugin {

    private static final Logger log = Logger.getLogger("Minecraft");
    private static Shop plugin;
    private ShopLogger logger = new ShopLogger(this, true);

    private ShopListener shopListener;
    private DisplayListener displayListener;
    private TransactionHandler transactionHandler;
    private MiscListener miscListener;
    private CreativeSelectionListener creativeSelectionListener;
    private ShopGUIListener guiListener;
    private Boolean worldGuardExists;
    private LWCHookListener lwcHookListener;
    private DynmapHookListener dynmapHookListener;
    private BluemapHookListener bluemapHookListener;
    private boolean bluemapEnabled;
    private BentoBoxHookListener bentoBoxHookListener;
    private ARMHookListener armHookListener;

    private ShopHandler shopHandler;
    private CommandHandler commandHandler;
    private ShopGuiHandler guiHandler;
    private EnderChestHandler enderChestHandler;
    private ShopMessage shopMessage;
    private ItemNameUtil itemNameUtil;
    private PriceUtil priceUtil;
    private ShopCreationUtil shopCreationUtil;

    private NMSBullshitHandler nmsBullshitHandler;

    private boolean usePerms;
    private boolean checkUpdates;
    private boolean enableGUI;
    private boolean hookWorldGuard;
    private boolean hookTowny;
    private String commandAlias;
    private DisplayType displayType;
    private DisplayTagOption displayTagOption;
    private DisplayType[] displayCycle;
    private boolean checkItemDurability;
    private boolean ignoreItemRepairCost;
    private boolean allowCreativeSelection;
    private boolean forceDisplayToNoneIfBlocked;
    private int displayLightLevel;
    private boolean setGlowingItemFrame;
    private boolean setGlowingSignText;
    private NavigableMap<Double, String> priceSuffixes;
    private Double priceSuffixMinimumValue;
    private boolean destroyShopRequiresSneak;
    private int hoursOfflineToRemoveShops;
    private boolean playSounds;
    private boolean playEffects;
    private boolean allowCreateMethodSign;
    private boolean allowCreateMethodChest;
    private boolean allowCreateMethodCommand;
    private ItemStack gambleDisplayItem;
    private ItemStack itemCurrency = null;
    private CurrencyType currencyType;
    private String currencyName = "";
    private String currencyFormat = "";
    private Economy econ = null;
    private List<Material> enabledContainers;
    private boolean inverseComboShops;
    private double creationCost;
    private double destructionCost;
    private double teleportCost;
    private double teleportCooldown;
    private boolean returnCreationCost;
    private boolean allowPartialSales;
    private double taxPercent;
    private boolean offlinePurchaseNotificationsEnabled;
    private ItemListType itemListType;
    private List<String> worldBlackList;
    private HashMap<ShopClickType, ShopAction> clickTypeActionMap;
    private NamespacedKey signLocationNameSpacedKey;
    private NamespacedKey playerUUIDNameSpacedKey;
    private LogHandler logHandler;

    private YamlConfiguration config;

    private boolean debug_allowUseOwnShop;
    private boolean debug_transactionDebugLogs;
    private int debug_shopCreateCooldown;
    private boolean debug_forceResaveAll;
    public static Shop getPlugin() {
        return plugin;
    }

    // Return the custom ShopLogger so that we can log at higher levels.
    @Override
    public ShopLogger getLogger() { return logger; }

    @Override
    public void onLoad(){
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("config.yml"), configFile);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // Load logger
        logger = new ShopLogger(this, config.getBoolean("enableLogColor"));
        this.getLogger().setLogLevel(config.getString("logLevel"));

        // look for the worldguard boolean, as it needs a flag registered before worldguard is enabled
        hookWorldGuard = config.getBoolean("hookWorldGuard");
        // Check if WorldGuard exists
        // Note: If WorldGuard exists we will check to verify a user can build in the region
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            this.getLogger().debug("WorldGuard detected, Shop will respect `passthrough`, `build`, and `chest-access` region flags during shop creation!");
            // Store for later
            this.worldGuardExists = true;
            // Check if we want to require `allow-shop: true` to exist on regions
            if(hookWorldGuard){
                this.getLogger().debug("Registering WorldGuard `allow-shop` flag...");
                // Register flag for WorldGuard if we are hooking into the flag system
                WorldGuardHook.registerAllowShopFlag();
                this.getLogger().debug("WorldGuard `allow-shop` flag restriction enabled, Shops can only be created in regions with the `allow-shop` flag set!");
            } else {
                this.getLogger().debug("WorldGuard `allow-shop` flag restriction is disabled, if you want to only allow shops in regions with the `allow-shop` flag, please set `hookWorldGuard` to `true` in `config.yml`");
            }
        } else {
            this.worldGuardExists = false;
        }
    }

    @Override
    public void onEnable() {
        plugin = this;

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("config.yml"), configFile);
        }

        File chatConfigFile = new File(getDataFolder(), "chatConfig.yml");
        if (!chatConfigFile.exists()) {
            chatConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("chatConfig.yml"), chatConfigFile);
        }

        File signConfigFile = new File(getDataFolder(), "signConfig.yml");
        if (!signConfigFile.exists()) {
            signConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("signConfig.yml"), signConfigFile);
        }

        File displayConfigFile = new File(getDataFolder(), "displayConfig.yml");
        if (!displayConfigFile.exists()) {
            displayConfigFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("displayConfig.yml"), displayConfigFile);
        }

        try {
            ConfigUpdater.update(plugin, "config.yml", configFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ConfigUpdater.update(plugin, "chatConfig.yml", chatConfigFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ConfigUpdater.update(plugin, "signConfig.yml", signConfigFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            ConfigUpdater.update(plugin, "displayConfig.yml", displayConfigFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }

        reloadConfig();
        signLocationNameSpacedKey = new NamespacedKey(this, "signLocation");
        playerUUIDNameSpacedKey = new NamespacedKey(this, "playerUUID");
        config = YamlConfiguration.loadConfiguration(configFile);
        // Load logger values again in case the log level was changed on a reload
        this.getLogger().setLogLevel(config.getString("logLevel"));
        this.getLogger().enableColor(config.getBoolean("enableLogColor"));

        nmsBullshitHandler = new NMSBullshitHandler(this);

        shopCreationUtil = new ShopCreationUtil(this);

        //removed item names file after item ids are no longer used. may revisit later with new materials
//        File itemNameFile = new File(getDataFolder(), "items.tsv");
//        if (!itemNameFile.exists()) {
//            itemNameFile.getParentFile().mkdirs();
//            UtilMethods.copy(getResource("items.tsv"), itemNameFile);
//        }

        //TODO
//        File pricesFile = new File(getDataFolder(), "prices.tsv");
//        if (!pricesFile.exists()) {
//            pricesFile.getParentFile().mkdirs();
//            UtilMethods.copy(getResource("prices.tsv"), pricesFile);
//        }

        shopListener = new ShopListener(this);
        transactionHandler = new TransactionHandler(this);
        miscListener = new MiscListener(this);
        creativeSelectionListener = new CreativeSelectionListener(this);
        displayListener = new DisplayListener(this);
        guiListener = new ShopGUIListener(this);

        //TODO set all config defaults here
        //config.setDefaults();

        try {
            displayType = DisplayType.valueOf(config.getString("displayType"));
        } catch (Exception e){ displayType = DisplayType.ITEM; }

        try {
            displayTagOption = DisplayTagOption.valueOf(config.getString("displayNameTags"));
        } catch (Exception e){ displayTagOption = DisplayTagOption.NONE; }

        try {
            List<String> cycle = config.getStringList("displayCycle");
            if(cycle.isEmpty()){
                for(DisplayType dt : DisplayType.values()){
                    cycle.add(dt.name());
                }
            }

            displayCycle = new DisplayType[cycle.size()];
            for(int i=0; i < cycle.size(); i++){
                displayCycle[i] = DisplayType.valueOf(cycle.get(i));
            }
        } catch (Exception e){ e.printStackTrace(); }

        //TODO in the future read the Skull Texture for display item directly from a value in the config file instead of its own serialized item file
//        ItemStack gambleDisplayItem = new ItemStack(Material.PLAYER_HEAD);
//        SkullMeta gambleDisplayItemMeta = (SkullMeta) gambleDisplayItem.getItemMeta();
//        GameProfile profile = new GameProfile(UUID.randomUUID(), "");
//
//        profile.getProperties().put("textures", new Property("texture", headTextureID));
//        try {
//            Field profileField = gambleDisplayItemMeta.getClass().getDeclaredField("profile");
//            profileField.setAccessible(true);
//            profileField.set(gambleDisplayItemMeta, profile);
//        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
//            e.printStackTrace();
//        }
//        gambleDisplayItem.setItemMeta(gambleDisplayItemMeta);


        shopMessage = new ShopMessage(this);
        itemNameUtil = new ItemNameUtil();
        priceUtil = new PriceUtil();

        File fileDirectory = new File(this.getDataFolder(), "Data");
        if (!fileDirectory.exists()) {
            boolean success;
            success = (fileDirectory.mkdirs());
            if (!success) {
                this.getLogger().severe("[Shop] Data folder could not be created!");
            }
        }

        allowCreateMethodSign = config.getBoolean("creationMethod.placeSign");
        allowCreateMethodChest = config.getBoolean("creationMethod.hitChest");
        allowCreateMethodCommand = config.getBoolean("creationMethod.runCommand");

        usePerms = config.getBoolean("usePermissions");
        if (usePerms) {
            this.getLogger().info("Permissions enabled, Shop will respect player permissions");
        } else {
            this.getLogger().info("Permissions disabled, everyone will be able to create/use shops by default");
        }
        checkUpdates = config.getBoolean("checkUpdates");
        enableGUI = config.getBoolean("enableGUI");
        hookWorldGuard = config.getBoolean("hookWorldGuard");
        hookTowny = config.getBoolean("hookTowny");
        bluemapEnabled = config.getBoolean("bluemap-marker.enabled");
        commandAlias = config.getString("commandAlias");
        checkItemDurability = config.getBoolean("checkItemDurability");
        ignoreItemRepairCost = config.getBoolean("ignoreItemRepairCost");
        allowCreativeSelection = config.getBoolean("allowCreativeSelection");
        forceDisplayToNoneIfBlocked = config.getBoolean("forceDisplayToNoneIfBlocked");
        displayLightLevel = config.getInt("displayLightLevel");
        setGlowingItemFrame = config.getBoolean("setGlowingItemFrame");
        hoursOfflineToRemoveShops = config.getInt("deletePlayerShopsAfterXHoursOffline");
        playSounds = config.getBoolean("playSounds");
        playEffects = config.getBoolean("playEffects");
        setGlowingSignText = config.getBoolean("setGlowingSignText");
        priceSuffixes = new TreeMap<>();
        for(String suffixKey : config.getConfigurationSection("priceSuffixes").getKeys(false)){
            if(suffixKey.equals("minimumValue")){
                priceSuffixMinimumValue = config.getDouble("priceSuffixes.minimumValue");
            }
            else {
                boolean enabled = config.getBoolean("priceSuffixes." + suffixKey + ".enabled");
                if (enabled) {
                    Double suffixValue = config.getDouble("priceSuffixes." + suffixKey + ".value");
                    priceSuffixes.put(suffixValue, suffixKey);
                }
            }
        }

        destroyShopRequiresSneak = config.getBoolean("destroyShopRequiresSneak");

        try {
            currencyType = CurrencyType.valueOf(config.getString("currency.type"));
        } catch(Exception e){
            currencyType = CurrencyType.ITEM;
        }

        offlinePurchaseNotificationsEnabled = config.getBoolean("offlinePurchaseNotifications.enabled");

        if (offlinePurchaseNotificationsEnabled && config.getString("logging.type").toUpperCase().equals("OFF")) {
            this.getLogger().warning("Offline purchase notifications are enabled in `config.yml` but DB logging is set to `OFF`. Offline purchase notifications will be disabled.");
            this.getLogger().warning("Please set `logging.type` to `FILE` or setup a database in `config.yml` to enable offline purchase notifications.");
            offlinePurchaseNotificationsEnabled = false;
        }

        //TODO
//        taxPercent = config.getDouble("taxPercent");

//        String itemCurrencyIDString = config.getString("itemCurrencyID");
//        int itemCurrencyId;
//        int itemCurrencyData = 0;
//        if (itemCurrencyIDString.contains(";")) {
//            itemCurrencyId = Integer.parseInt(itemCurrencyIDString.substring(0, itemCurrencyIDString.indexOf(";")));
//            itemCurrencyData = Integer.parseInt(itemCurrencyIDString.substring(itemCurrencyIDString.indexOf(";") + 1, itemCurrencyIDString.length()));
//        } else {
//            itemCurrencyId = Integer.parseInt(itemCurrencyIDString.substring(0, itemCurrencyIDString.length()));
//        }
//
//        itemCurrency = new ItemStack(itemCurrencyId);
//        itemCurrency.setData(new MaterialData(itemCurrencyId, (byte) itemCurrencyData));


        //Loading the itemCurrency from a file makes it easier to allow servers to use detailed itemstacks as the server's economy item
        File itemCurrencyFile = new File(fileDirectory, "itemCurrency.yml");
        if(itemCurrencyFile.exists()){
            YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
            itemCurrency = currencyConfig.getItemStack("item");
            itemCurrency.setAmount(1);
        }
        else{
            try {
                itemCurrency = new ItemStack(Material.EMERALD);
                itemCurrencyFile.createNewFile();

                YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
                currencyConfig.set("item", itemCurrency);
                currencyConfig.save(itemCurrencyFile);
            } catch (Exception e) {}
        }

        //load the gamble display item from it's file
        File gambleDisplayFile = new File(fileDirectory, "gambleDisplayItem.yml");
        if (!gambleDisplayFile.exists()) {
            gambleDisplayFile.getParentFile().mkdirs();
            UtilMethods.copy(getResource("GAMBLE_DISPLAY.yml"), gambleDisplayFile);
        }
        YamlConfiguration gambleItemConfig = YamlConfiguration.loadConfiguration(gambleDisplayFile);
        gambleDisplayItem = gambleItemConfig.getItemStack("GAMBLE_DISPLAY");

        currencyName = config.getString("currency.name");
        currencyFormat = config.getString("currency.format");

        enabledContainers = new ArrayList<>();
        for(String materialString : config.getStringList("enabledContainers")){
            try{
                enabledContainers.add(Material.valueOf(materialString));
            } catch(IllegalArgumentException e) {}
        }

        inverseComboShops = config.getBoolean("inverseComboShops");

        creationCost = config.getDouble("creationCost");
        destructionCost = config.getDouble("destructionCost");
        teleportCost = config.getDouble("teleportCost");
        teleportCooldown = config.getDouble("teleportCooldown");
        returnCreationCost = config.getBoolean("returnCreationCost");
        allowPartialSales = config.getBoolean("allowPartialSales");

        try {
            itemListType = ItemListType.valueOf(config.getString("itemList"));
        } catch(Exception e){
            itemListType = ItemListType.NONE;
        }

        worldBlackList = config.getStringList("worldBlacklist");
        for(String world : config.getStringList("worldBlacklist")){
            worldBlackList.add(world);
        }

        clickTypeActionMap = new HashMap<>();
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.transactWithShop")), ShopAction.TRANSACT);
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.transactWithShopFullStack")), ShopAction.TRANSACT_FULLSTACK);
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.viewShopDetails")), ShopAction.VIEW_DETAILS);
        clickTypeActionMap.put(ShopClickType.valueOf(config.getString("actionMappings.cycleShopDisplay")), ShopAction.CYCLE_DISPLAY);

        // Check if we should load VAULT economy
        if (currencyType == CurrencyType.VAULT) {
            if (setupEconomy()) {
                this.getLogger().info("Shops will use the Vault economy (" + currencyName + ") as currency on the server.");
            } else {
                this.getLogger().severe("Unable to connect to Vault! Is the plugin installed?");
                this.getLogger().severe("Plugin Disabled: Invalid configuration value `economy.type` config.yml. If you do not wish to use Vault with Shop, make sure to set `economy.type` in the config file to `ITEM`.");
                getServer().getPluginManager().disablePlugin(plugin);
            }
        } else {
            if (itemCurrency == null) {
                this.getLogger().severe("Plugin Disabled: Invalid value for `itemCurrencyID` in `config.yml`");
                getServer().getPluginManager().disablePlugin(plugin);
            }
            this.getLogger().info("Shops will use " + itemNameUtil.getName(itemCurrency).toPlainText() + "(s) as the currency on the server.");
        }

        commandHandler = new CommandHandler(this, null, commandAlias, "Base command for the Shop plugin", "/shop", new ArrayList(Arrays.asList(commandAlias)));
        //this.getCommand(commandAlias).setExecutor(new CommandHandler(this));
        //this.getCommand(commandAlias).setTabCompleter(new CommandTabCompleter());
        //this.getCommand(commandAlias).setAliases(new ArrayList<>())

        //check for unregistered enchantments when new MC updates come out
        /*for (Enchantment enchantment : Enchantment.values()){
            if(UtilMethods.getEnchantmentName(enchantment).equals("Unknown")){
                System.out.println("[Shop] warning: unregistered enchantment: "+enchantment.getName());
            }
        }*/

        guiHandler = new ShopGuiHandler(plugin);
        shopHandler = new ShopHandler(plugin);
        guiHandler.loadIconsAndTitles();
        enderChestHandler = new EnderChestHandler(plugin);
        logHandler = new LogHandler(plugin, config);

        getServer().getPluginManager().registerEvents(displayListener, this);
        getServer().getPluginManager().registerEvents(shopListener, this);
        getServer().getPluginManager().registerEvents(miscListener, this);
        getServer().getPluginManager().registerEvents(creativeSelectionListener, this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        //only define different listener hooks if the plugins are present on the server
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            this.getLogger().notice("WorldGuard is installed, creating WorldGuard listener");
            this.getLogger().helpful("Shop will respect WorldGuard `passthrough`, `build`, and `chest-access` region flags during shop creation!");
            // Store for later
            this.worldGuardExists = true;
            // Check if we want to require `allow-shop: true` to exist on regions
            if(hookWorldGuard){
                this.getLogger().helpful("WorldGuard `allow-shop` flag restriction enabled, Shops can only be created in regions with the `allow-shop` flag set!");
                // Register flag for WorldGuard if we are hooking into the flag system
                // Only log, don't re-register flags, we do that in the onLoad function.
//                WorldGuardHook.registerAllowShopFlag();
            } else {
                this.getLogger().helpful("WorldGuard `allow-shop` flag restriction is disabled, if you want to only allow shops in regions with the `allow-shop` flag, please set `hookWorldGuard` to `true` in `config.yml`");
            }
        } else {
            this.worldGuardExists = false;
        }

        if(getServer().getPluginManager().getPlugin("Towny") != null && this.hookTowny){
            this.getLogger().notice("Towny is installed, Shop will respect Towny!");
        }

        if(getServer().getPluginManager().getPlugin("LWC") != null){
            lwcHookListener = new LWCHookListener(this);
            getServer().getPluginManager().registerEvents(lwcHookListener, this);
            this.getLogger().notice("LWC is installed, creating LWC listener");
        }

        if(getServer().getPluginManager().getPlugin("dynmap") != null){
            dynmapHookListener = new DynmapHookListener(this);
            getServer().getPluginManager().registerEvents(dynmapHookListener, this);
            this.getLogger().notice("Dynmap is installed, creating Dynmap listener");
        }

        if(getServer().getPluginManager().getPlugin("BlueMap") != null && bluemapEnabled){
            plugin.getLogger().notice("BlueMap is installed, starting BlueMap integration");
            // Wait for 2 minutes for BlueMap to become available/boot up, then initialize listener.
            new BukkitRunnable() {
                @Override
                public void run() {
                    BlueMapAPI.getInstance().ifPresent(api -> {
                        plugin.getLogger().debug("BlueMap is ready, creating BlueMap listener");
                        bluemapHookListener = new BluemapHookListener(plugin);
                        getServer().getPluginManager().registerEvents(bluemapHookListener, plugin);
                        // Make sure we load the markers in case there are shops that BlueMap doesn't know about
                        bluemapHookListener.reloadMarkers(shopHandler);
                        // Mark the task as complete and cancel the timer
                        cancel();
                    });
                }
            }.runTaskTimer(plugin, 20, 20); // Check every second (20 ticks) until BlueMap is booted
        }

        if(getServer().getPluginManager().getPlugin("BentoBox") != null){
            bentoBoxHookListener = new BentoBoxHookListener(this);
            getServer().getPluginManager().registerEvents(bentoBoxHookListener, this);
            this.getLogger().notice("BentoBox is installed, creating BentoBox listener");
        }

        if(getServer().getPluginManager().getPlugin("AdvancedRegionMarket") != null){
            armHookListener = new ARMHookListener(this);
            getServer().getPluginManager().registerEvents(armHookListener, this);
            this.getLogger().notice("AdvancedRegionMarket is installed, creating AdvancedRegionMarket listener");
        }

        int bstatsPluginId = 25211;
        Metrics metrics = new Metrics(plugin, bstatsPluginId);
        // transactions would be cool
        // It would also be cool to see the number of items transacted (bought/sold & item currency)
        // I don't think showing vault currency is worth it, since people have vastly different economy scaling
        // It would be worth it to show a pie chart of what economy type is being used!
        metrics.addCustomChart(new SingleLineChart("transactions", () -> logHandler.getRecentTransactionCount()));
        metrics.addCustomChart(new SingleLineChart("item_volume", () -> logHandler.getRecentItemVolume()));
        metrics.addCustomChart(new SingleLineChart("shops", () -> shopHandler.getNumberOfShops()));
        metrics.addCustomChart(new AdvancedPie("shop_types", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Buy", shopHandler.getNumberOfShops(ShopType.BUY));
            valueMap.put("Sell", shopHandler.getNumberOfShops(ShopType.SELL));
            valueMap.put("Barter", shopHandler.getNumberOfShops(ShopType.BARTER));
            valueMap.put("Combo", shopHandler.getNumberOfShops(ShopType.COMBO));
            valueMap.put("Gamble", shopHandler.getNumberOfShops(ShopType.GAMBLE));
            return valueMap;
        }));
        metrics.addCustomChart(new SimplePie("economy_type", () -> { return currencyType.toString(); }));
        
        // Add metrics for more configuration options
        metrics.addCustomChart(new SimplePie("use_permissions", () -> String.valueOf(usePerms)));
        metrics.addCustomChart(new SimplePie("allow_partial_sales", () -> String.valueOf(allowPartialSales)));

        // Group these into an advanced pie
        metrics.addCustomChart(new AdvancedPie("shop_creation_methods", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Sign Creation", allowCreateMethodSign ? 1 : 0);
            valueMap.put("Chest Creation", allowCreateMethodChest ? 1 : 0);
            valueMap.put("Command Creation", allowCreateMethodCommand ? 1 : 0);
            valueMap.put("Signs Disabled", allowCreateMethodSign ? 0 : 1);
            valueMap.put("Chests Disabled", allowCreateMethodChest ? 0 : 1);
            valueMap.put("Commands Disabled", allowCreateMethodCommand ? 0 : 1);
            return valueMap;
        }));

        metrics.addCustomChart(new SimplePie("offline_purchase_notifications", () -> String.valueOf(offlinePurchaseNotificationsEnabled)));
        metrics.addCustomChart(new SimplePie("shop_gui_enabled", () -> { return String.valueOf(enableGUI); }));
        metrics.addCustomChart(new SimplePie("allow_searching_items", () -> String.valueOf(allowCreativeSelection)));
        metrics.addCustomChart(new SimplePie("check_item_durability", () -> String.valueOf(checkItemDurability)));
        metrics.addCustomChart(new SimplePie("ignore_item_repair_cost", () -> String.valueOf(ignoreItemRepairCost)));
        metrics.addCustomChart(new AdvancedPie("sounds_and_effects", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("Sounds Enabled", playSounds ? 1 : 0);
            valueMap.put("Effects Enabled", playEffects ? 1 : 0);
            valueMap.put("Sounds Disabled", playSounds ? 0 : 1);
            valueMap.put("Effects Disabled", playEffects ? 0 : 1);
            return valueMap;
        }));
        
        metrics.addCustomChart(new SimplePie("worldguard_enabled", () -> { return String.valueOf(hookWorldGuard); }));
        metrics.addCustomChart(new SimplePie("towny_enabled", () -> { return String.valueOf(hookTowny); }));
        metrics.addCustomChart(new SimplePie("bluemap_enabled", () -> String.valueOf(bluemapEnabled)));
        metrics.addCustomChart(new SimplePie("database_type", () -> String.valueOf(config.getString("logging.type"))));
        
        // Track display type preferences
        metrics.addCustomChart(new SimplePie("item_hover_display_type", () -> displayType.toString()));
        metrics.addCustomChart(new SimplePie("hover_text_activation_type", () -> displayTagOption.toString()));
        
        // Track if shop auto-deletion is enabled
        metrics.addCustomChart(new SimplePie("auto_cleanup_dead_shops", () -> String.valueOf(hoursOfflineToRemoveShops > 0)));

        debug_allowUseOwnShop = config.getBoolean("debug.allowUseOwnShop");
        debug_transactionDebugLogs = config.getBoolean("debug.transactionDebugLogs");
        debug_shopCreateCooldown = config.getInt("debug.shopCreateCooldown");
        debug_forceResaveAll = config.getBoolean("debug.forceResaveAll");

        displayListener.startRepeatingDisplayViewTask();

        this.getLogger().info("Enabled Shop " + this.getDescription().getVersion());

        if(checkUpdates){
            new UpdateChecker(this).checkForUpdate();
        }
    }

    @Override
    public void onDisable(){
        displayListener.cancelRepeatingViewTask();

        // Save all the shops that need to be updated
        shopHandler.saveAllShops();

        this.getLogger().info("Disabled Shop " + this.getDescription().getVersion());
    }

    public void reload(){
        this.getLogger().info("Reloading Shop " + this.getDescription().getVersion());

        HandlerList.unregisterAll(displayListener);
        HandlerList.unregisterAll(shopListener);
        HandlerList.unregisterAll(miscListener);
        HandlerList.unregisterAll(creativeSelectionListener);
        HandlerList.unregisterAll(guiListener);
        if(lwcHookListener != null){
            HandlerList.unregisterAll(lwcHookListener);
        }
        if(dynmapHookListener != null){
            dynmapHookListener.deleteMarkers();
            HandlerList.unregisterAll(dynmapHookListener);
        }
        if(bluemapHookListener != null){
            //bluemapHookListener.deleteMarkers();
            HandlerList.unregisterAll(bluemapHookListener);
        }
        if(bentoBoxHookListener != null){
            HandlerList.unregisterAll(bentoBoxHookListener);
        }
        if(armHookListener != null){
            HandlerList.unregisterAll(armHookListener);
        }

        plugin.getShopHandler().removeAllDisplays(null);

        onDisable();
        onEnable();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        this.getLogger().notice("Vault is installed, creating Vault integration for Economy support");
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public ShopListener getShopListener() {
        return shopListener;
    }

    public DisplayListener getDisplayListener() {
        return displayListener;
    }

    public MiscListener getMiscListener(){
        return miscListener;
    }

    public CreativeSelectionListener getCreativeSelectionListener() {
        return creativeSelectionListener;
    }

    public BluemapHookListener getBluemapHookListener() {
        return bluemapHookListener;
    }

    public TransactionHandler getTransactionHelper() {
        return transactionHandler;
    }

    public ShopHandler getShopHandler() {
        return shopHandler;
    }

    public ShopGuiHandler getGuiHandler(){
        return guiHandler;
    }

    public EnderChestHandler getEnderChestHandler(){
        return enderChestHandler;
    }

    public boolean usePerms() {
        return usePerms;
    }

    public boolean getAllowCreationMethodSign(){
        return allowCreateMethodSign;
    }

    public boolean getAllowCreationMethodChest(){
        return allowCreateMethodChest;
    }

    public boolean getAllowCreationMethodCommand(){
        return allowCreateMethodCommand;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    public boolean hookWorldGuard(){
        return hookWorldGuard;
    }

    public boolean worldGuardExists() { return worldGuardExists; }

    public boolean hookTowny(){
        return hookTowny;
    }

    public DisplayType getDisplayType(){
        return displayType;
    }

    public DisplayTagOption getDisplayTagOption(){
        return displayTagOption;
    }

    public DisplayType[] getDisplayCycle(){
        return displayCycle;
    }

    public boolean checkItemDurability(){
        return checkItemDurability;
    }
    public boolean ignoreItemRepairCost(){
        return ignoreItemRepairCost;
    }

    public boolean allowCreativeSelection(){
        return allowCreativeSelection;
    }

    public boolean forceDisplayToNoneIfBlocked() {
        return forceDisplayToNoneIfBlocked;
    }

    public int getDisplayLightLevel(){
        return displayLightLevel;
    }

    public boolean getGlowingItemFrame(){
        return setGlowingItemFrame;
    }

    public int getHoursOfflineToRemoveShops(){
        return hoursOfflineToRemoveShops;
    }

    public boolean playSounds(){
        return playSounds;
    }

    public boolean playEffects(){
        return playEffects;
    }

    public boolean getGlowingSignText(){
        return setGlowingSignText;
    }

    public NavigableMap<Double, String> getPriceSuffixes(){
        return priceSuffixes;
    }

    public Double getPriceSuffixMinimumValue(){
        return priceSuffixMinimumValue;
    }

    public boolean useGUI(){
        return enableGUI;
    }

    public ItemStack getGambleDisplayItem(){
        return gambleDisplayItem;
    }

    public ItemStack getItemCurrency() {
        return itemCurrency;
    }

    public boolean offlinePurchaseNotificationsEnabled() {
        return offlinePurchaseNotificationsEnabled;
    }

    public boolean getDebug_allowUseOwnShop() { return debug_allowUseOwnShop; }
    public boolean getDebug_transactionDebugLogs() { return debug_transactionDebugLogs; }
    public int getDebug_shopCreateCooldown() { return debug_shopCreateCooldown; }
    public boolean getDebug_forceResaveAll() { return debug_forceResaveAll; }

    public void setItemCurrency(ItemStack itemCurrency){
        this.itemCurrency = itemCurrency;

        try {
            File fileDirectory = new File(getDataFolder(), "Data");
            File itemCurrencyFile = new File(fileDirectory, "itemCurrency.yml");
            YamlConfiguration currencyConfig = YamlConfiguration.loadConfiguration(itemCurrencyFile);
            currencyConfig.set("item", plugin.getItemCurrency());
            currencyConfig.save(itemCurrencyFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setGambleDisplayItem(ItemStack is){
        this.gambleDisplayItem = is;

        try{
            File fileDirectory = new File(plugin.getDataFolder(), "Data");
            File gambleDisplayFile = new File(fileDirectory, "gambleDisplayItem.yml");
            if (!gambleDisplayFile.exists()) {
                gambleDisplayFile.getParentFile().mkdirs();
                gambleDisplayFile.createNewFile();
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(gambleDisplayFile);

            config.set("GAMBLE_DISPLAY", is);
            config.save(gambleDisplayFile);

            plugin.reload();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public String getCommandAlias(){
        return commandAlias;
    }

    public String getPriceString(double price, boolean pricePer){
        if(price == 0){
            return ShopMessage.getFreePriceWord();
        }

        String format = currencyFormat;

        if(format.contains("[name]")){
            format = format.replace("[name]", currencyName);
        }
        if(format.contains("[price]")){
            if(currencyType == CurrencyType.VAULT) {
                return format.replace("[price]", UtilMethods.formatLongToKString(price, true));
                //return format.replace("[price]", new DecimalFormat("0.00").format(price).toString());
            }
            else if(pricePer) {
                return format.replace("[price]", UtilMethods.formatLongToKString(price, false));
                //return format.replace("[price]", new DecimalFormat("#.##").format(price).toString());
            }
            else
                return format.replace("[price]", ""+(int)price);
        }
        return format;
    }

    public String getPriceComboString(double price, double priceSell, boolean pricePer){
        if(price == 0){
            return ShopMessage.getFreePriceWord();
        }

        String format = currencyFormat;

        if(format.contains("[name]")){
            format = format.replace("[name]", currencyName);
        }
        if(format.contains("[price]")){
            if(currencyType == CurrencyType.VAULT)
                //return format.replace("[price]", new DecimalFormat("0.00").format(price)+"/"+new DecimalFormat("0.00").format(priceSell).toString());
            return format.replace("[price]", UtilMethods.formatLongToKString(price, true)+"/"+UtilMethods.formatLongToKString(priceSell, true));
            else if(pricePer)
                //return format.replace("[price]", new DecimalFormat("#.##").format(price).toString()+"/"+new DecimalFormat("0.00").format(priceSell).toString());
                return format.replace("[price]", UtilMethods.formatLongToKString(price, false)+"/"+UtilMethods.formatLongToKString(priceSell, true));
            else
                return format.replace("[price]", ""+(int)price+"/"+(int)priceSell);
        }
        return format;
    }

    public double getTaxPercent(){
        return taxPercent;
    }

    public Economy getEconomy() {

        if (econ == null) {
            setupEconomy();
        }

        return econ;
    }

    public List<Material> getEnabledContainers(){
        return enabledContainers;
    }

    public boolean useEnderChests(){
        return enabledContainers.contains(Material.ENDER_CHEST);
    }

    public boolean inverseComboShops(){
        return inverseComboShops;
    }

    public double getCreationCost(){
        return creationCost;
    }

    public double getDestructionCost(){
        return destructionCost;
    }

    public boolean getDestroyShopRequiresSneak(){
        return destroyShopRequiresSneak;
    }

    public double getTeleportCost(){
        return teleportCost;
    }

    public double getTeleportCooldown(){
        return teleportCooldown;
    }

    public boolean returnCreationCost(){
        return returnCreationCost;
    }

    public boolean getAllowPartialSales(){
        return allowPartialSales;

    }

    public ItemNameUtil getItemNameUtil(){
        return itemNameUtil;
    }

    public ShopCreationUtil getShopCreationUtil(){
        return shopCreationUtil;
    }

    public ItemListType getItemListType(){
        return itemListType;
    }

    public List<String> getWorldBlacklist(){
        return worldBlackList;
    }

    public ShopAction getShopAction(ShopClickType shopClickType){
        return clickTypeActionMap.get(shopClickType);
    }

    public NMSBullshitHandler getNmsBullshitHandler() {
        return nmsBullshitHandler;
    }

    public NamespacedKey getSignLocationNameSpacedKey(){
        return signLocationNameSpacedKey;
    }

    public NamespacedKey getPlayerUUIDNameSpacedKey(){
        return playerUUIDNameSpacedKey;
    }

    public LogHandler getLogHandler(){
        return logHandler;
    }
}
