package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.ArmorStandData;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Implémentation moderne pour Paper 1.21.3+
 * Utilise les APIs Paper propres sans NMS
 */
public class PaperDisplay extends AbstractDisplay {

    public PaperDisplay(Location shopSignLocation) {
        super(shopSignLocation);
    }

    @Override
    protected void spawnItemPacket(Player player, ItemStack is, Location location) {
        // Paper 1.21.3+ a des APIs beaucoup plus propres pour spawner des items
        Item item = location.getWorld().dropItem(location, is);
        item.setPickupDelay(Integer.MAX_VALUE); // Empêcher le ramassage
        item.setVelocity(new Vector(0, 0, 0)); // Pas de mouvement
        item.setGravity(false); // Pas de gravité
        item.setPersistent(true); // Ne disparaît pas

        // Stocker l'entité pour le nettoyage
        addEntityToList(player, item.getEntityId());
    }

    @Override
    protected void spawnArmorStandPacket(Player player, ArmorStandData armorStandData, String text) {
        Location loc = armorStandData.getLocation();

        ArmorStand armorStand = loc.getWorld().spawn(loc, ArmorStand.class);

        // Configurer l'armor stand
        armorStand.setVisible(true);
        armorStand.setSmall(armorStandData.isSmall());
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setPersistent(true);
        armorStand.setInvulnerable(true);
        armorStand.setCollidable(false);

        // Définir l'équipement si disponible
        if (armorStandData.getEquipment() != null) {
            EquipmentSlot slot = armorStandData.getEquipmentSlot();
            if (slot != null) {
                armorStand.getEquipment().setItem(slot, armorStandData.getEquipment());
            } else {
                // Par défaut main hand
                armorStand.getEquipment().setItemInMainHand(armorStandData.getEquipment());
            }
        }

        // Définir les poses
        if (armorStandData.getRightArmPose() != null) {
            armorStand.setRightArmPose(armorStandData.getRightArmPose());
        }

        // Définir la rotation
        Location rotatedLoc = loc.clone();
        rotatedLoc.setYaw((float) armorStandData.getYaw());
        armorStand.teleport(rotatedLoc);

        // Définir le nom personnalisé si fourni
        if (text != null) {
            armorStand.setCustomName(text);
            armorStand.setCustomNameVisible(true);
        }

        addEntityToList(player, armorStand.getEntityId());
    }

    @Override
    protected void spawnItemFramePacket(Player player, ItemStack is, Location location, BlockFace facing, boolean isGlowing) {
        // Paper 1.21.3+ a des APIs beaucoup plus propres pour les item frames
        Class<? extends ItemFrame> frameClass = isGlowing ? GlowItemFrame.class : ItemFrame.class;

        ItemFrame frame = location.getWorld().spawn(location, frameClass);
        frame.setFacingDirection(facing);
        frame.setItem(is);
        frame.setFixed(true); // Empêcher la rotation par les joueurs
        frame.setVisible(false); // Cacher le frame lui-même

        addEntityToList(player, frame.getEntityId());
    }

    @Override
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        // Supprimer les entités pour le joueur spécifié
        if (entityIDs.containsKey(player.getUniqueId())) {
            ArrayList<Integer> entities = entityIDs.get(player.getUniqueId());
            for (Integer entityId : entities) {
                // Trouver et supprimer l'entité
                for (Entity entity : player.getWorld().getEntities()) {
                    if (entity.getEntityId() == entityId) {
                        entity.remove();
                        break;
                    }
                }
            }
            entityIDs.remove(player.getUniqueId());
        }

        if (onlyDisplayTags && displayTagEntityIDs.containsKey(player.getUniqueId())) {
            ArrayList<Integer> tagEntities = displayTagEntityIDs.get(player.getUniqueId());
            for (Integer entityId : tagEntities) {
                // Trouver et supprimer l'entité tag
                for (Entity entity : player.getWorld().getEntities()) {
                    if (entity.getEntityId() == entityId) {
                        entity.remove();
                        break;
                    }
                }
            }
            displayTagEntityIDs.remove(player.getUniqueId());
        }
    }

    // Méthode spécifique à PaperDisplay - pas d'override
    public String getItemNameNMS(ItemStack item) {
        // Paper moderne : utilise les APIs Bukkit propres au lieu de NMS
        return Shop.getPlugin().getItemNameUtil().getName(item).toPlainText();
    }

    public String getVersion() {
        return "Paper-1.21.3";
    }

    // Méthode helper pour ajouter une entité à la liste de tracking
    private void addEntityToList(Player player, int entityId) {
        UUID playerId = player.getUniqueId();
        if (!entityIDs.containsKey(playerId)) {
            entityIDs.put(playerId, new ArrayList<>());
        }
        entityIDs.get(playerId).add(entityId);
    }
}
