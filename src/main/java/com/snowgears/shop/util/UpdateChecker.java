package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

//author: Benz56
//https://github.com/Benz56/Async-Update-Checker/blob/master/UpdateChecker.java

public class UpdateChecker {

    private final Shop plugin;
    private final String localPluginVersion;
    private String spigotPluginVersion;

    //Constants. Customize to your liking.
    private static final int ID = 9628; //The ID of your resource. Can be found in the resource URL.
    private static final String ERR_MSG = "&cShop update checker failed!";
    private static final String UPDATE_MSG = "&fNew Shop update &av{latestReleasedVersion} &fis available &f(your running: &ev{runningVersion}&f)\n&bhttps://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/updates";
    private static final String DEV_VERSION_MSG = "&l&e[Shop] &r&6Running development version &l&c{runningVersion}&r&6. If you encounter bugs, please roll back to the latest stable version.";
    //PermissionDefault.FALSE == OPs need the permission to be notified.
    //PermissionDefault.TRUE == all OPs are notified regardless of having the permission.
    private static final Permission UPDATE_PERM = new Permission("shop.update", PermissionDefault.FALSE);
    private static final long CHECK_INTERVAL = 12_000; //In ticks.
    private BukkitTask task;


    public UpdateChecker(final Shop plugin) {
        this.plugin = plugin;
        this.localPluginVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdate() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                //The request is executed asynchronously as to not block the main thread.
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    //Request the current version of your plugin on SpigotMC.
                    try {
                        final HttpsURLConnection connection = (HttpsURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=" + ID).openConnection();
                        connection.setRequestMethod("GET");
                        spigotPluginVersion = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                    } catch (final IOException e) {
                        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', ERR_MSG));
                        e.printStackTrace();
                        cancelTask();
                        return;
                    }

                    // Are we running the current version?
                    if (compareVersions(localPluginVersion, spigotPluginVersion) == 0) return;
                    // Are we running an older verion?
                    if (compareVersions(localPluginVersion, spigotPluginVersion) < 0) {
                        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', embedVersions(UPDATE_MSG, localPluginVersion, spigotPluginVersion)));

                        //Register the PlayerJoinEvent
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().registerEvents(new Listener() {
                            @EventHandler(priority = EventPriority.MONITOR)
                            public void onPlayerJoin(final PlayerJoinEvent event) {
                                final Player player = event.getPlayer();
                                if (!player.isOp()) return;
                                TextComponent updateMsg = new TextComponent(ChatColor.translateAlternateColorCodes('&', embedVersions(UPDATE_MSG, localPluginVersion, spigotPluginVersion)));
                                updateMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to open Shop plugin page").create()));
                                updateMsg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/shop-the-intuitive-shop-plugin.9628/updates"));
                                player.spigot().sendMessage(updateMsg);
                            }
                        }, plugin));
                    }
                    /* RUNNING SHOP DEV VERSION */
                    if (compareVersions(localPluginVersion, spigotPluginVersion) > 0) {
                        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', embedVersions(DEV_VERSION_MSG, localPluginVersion, spigotPluginVersion)));

                        //Register the PlayerJoinEvent
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().registerEvents(new Listener() {
                            @EventHandler(priority = EventPriority.MONITOR)
                            public void onPlayerJoin(final PlayerJoinEvent event) {
                                final Player player = event.getPlayer();
                                if (!player.isOp()) return;
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', embedVersions(DEV_VERSION_MSG, localPluginVersion, spigotPluginVersion)));
                            }
                        }, plugin));
                    }

                    cancelTask(); //Cancel the runnable as an update has been found.
                });
            }
        }.runTaskTimer(plugin, 1, CHECK_INTERVAL);
    }

    public void cancelTask() {
        if (task != null) {
            task.cancel();
        }
    }

    private String embedVersions(String msg, String runningVersion, String latestReleasedVersion) {
        return msg.replace("{runningVersion}", runningVersion).replace("{latestReleasedVersion}", latestReleasedVersion);
    }

    public int compareVersions(String localVersion, String latestVersion) {
        // Split versions into base version and tag
        String[] v1Parts = localVersion.split("-", 2);
        String[] v2Parts = latestVersion.split("-", 2);

        String v1Base = v1Parts[0];
        String v2Base = v2Parts[0];

        String v1Tag = v1Parts.length > 1 ? v1Parts[1] : "";
        String v2Tag = v2Parts.length > 1 ? v2Parts[1] : "";

        // Split base versions into numeric parts
        String[] v1Nums = v1Base.split("\\.");
        String[] v2Nums = v2Base.split("\\.");

        int maxLength = Math.max(v1Nums.length, v2Nums.length);

        for (int i = 0; i < maxLength; i++) {
            int n1 = i < v1Nums.length ? Integer.parseInt(v1Nums[i]) : 0;
            int n2 = i < v2Nums.length ? Integer.parseInt(v2Nums[i]) : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }

        // If base versions are the same, compare tags
        // No tag is considered higher than any tag (stable release vs pre-release)
        if (v1Tag.isEmpty() && !v2Tag.isEmpty()) return 1;
        if (!v1Tag.isEmpty() && v2Tag.isEmpty()) return -1;
        if (v1Tag.isEmpty() && v2Tag.isEmpty()) return 0;

        // Both have tags, compare them lexicographically
        return v1Tag.compareTo(v2Tag);
    }
}