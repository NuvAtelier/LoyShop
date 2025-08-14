package com.snowgears.shop.hook;

import com.snowgears.shop.Shop;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Hook simplifié pour l'intégration avec le plugin Lands
 * Utilise la réflexion pour éviter les dépendances directes
 */
public class LandsHookListener implements Listener {

    private Shop plugin;
    private boolean landsAvailable = false;

    public LandsHookListener(Shop plugin) {
        this.plugin = plugin;
        // Vérifier si Lands est disponible via réflexion
        try {
            Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            this.landsAvailable = true;
            plugin.getLogger().info("Lands API détectée - support activé");
        } catch (ClassNotFoundException e) {
            this.landsAvailable = false;
            plugin.getLogger().info("Lands API non disponible - support désactivé");
        }
    }

    /**
     * Vérifie si un joueur peut créer un shop à la location donnée
     * @param player Le joueur
     * @param location La location où créer le shop
     * @return true si le joueur peut créer le shop, false sinon
     */
    public boolean canCreateShop(Player player, Location location) {
        if (!landsAvailable) {
            return true; // Si Lands n'est pas disponible, autoriser
        }

        try {
            // Utiliser la réflexion pour interagir avec Lands API
            Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            Object landsIntegration = landsIntegrationClass.getMethod("of", org.bukkit.plugin.Plugin.class).invoke(null, plugin);

            Object land = landsIntegrationClass.getMethod("getLandByUnclaimedChunk", org.bukkit.World.class, int.class, int.class)
                .invoke(landsIntegration, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);

            if (land == null) {
                return true; // Pas de terre réclamée, autoriser
            }

            // Vérifier les permissions via réflexion
            Class<?> roleSettingClass = Class.forName("me.angeschossen.lands.api.role.enums.RoleSetting");
            Object blockPlaceFlag = roleSettingClass.getField("BLOCK_PLACE").get(null);

            Boolean canBuild = (Boolean) land.getClass().getMethod("hasRoleFlag", java.util.UUID.class, roleSettingClass)
                .invoke(land, player.getUniqueId(), blockPlaceFlag);

            return canBuild != null ? canBuild : false;

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification des permissions Lands : " + e.getMessage());
            return true; // En cas d'erreur, autoriser par défaut
        }
    }

    /**
     * Vérifie si un joueur peut utiliser un shop à la location donnée
     * @param player Le joueur
     * @param location La location du shop
     * @return true si le joueur peut utiliser le shop, false sinon
     */
    public boolean canUseShop(Player player, Location location) {
        if (!landsAvailable) {
            return true; // Si Lands n'est pas disponible, autoriser
        }

        try {
            // Utiliser la réflexion pour interagir avec Lands API
            Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            Object landsIntegration = landsIntegrationClass.getMethod("of", org.bukkit.plugin.Plugin.class).invoke(null, plugin);

            Object land = landsIntegrationClass.getMethod("getLandByUnclaimedChunk", org.bukkit.World.class, int.class, int.class)
                .invoke(landsIntegration, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);

            if (land == null) {
                return true; // Pas de terre réclamée, autoriser
            }

            // Vérifier les permissions via réflexion
            Class<?> roleSettingClass = Class.forName("me.angeschossen.lands.api.role.enums.RoleSetting");
            Object interactContainerFlag = roleSettingClass.getField("INTERACT_CONTAINER").get(null);

            Boolean canInteract = (Boolean) land.getClass().getMethod("hasRoleFlag", java.util.UUID.class, roleSettingClass)
                .invoke(land, player.getUniqueId(), interactContainerFlag);

            return canInteract != null ? canInteract : false;

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification des permissions Lands : " + e.getMessage());
            return true; // En cas d'erreur, autoriser par défaut
        }
    }

    public boolean isLandsAvailable() {
        return landsAvailable;
    }
}
