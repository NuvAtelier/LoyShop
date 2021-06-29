package com.snowgears.shop.display;

import com.snowgears.shop.Shop;
import com.snowgears.shop.util.UtilMethods;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class LegacyDisplay {

    public static boolean isDisplay(Entity entity){
        if(UtilMethods.isMCVersion14Plus()) {
            PersistentDataContainer persistentData = entity.getPersistentDataContainer();
            if (persistentData != null) {
                try {
                    int dataDisplay = persistentData.get(new NamespacedKey(Shop.getPlugin(), "display"), PersistentDataType.INTEGER);
                    return (dataDisplay == 1);
                } catch (NullPointerException e) {
                    return false;
                }
            }
            return false;
        }
        return isDisplayLegacy(entity);
    }

    public static boolean isDisplayLegacy(Entity entity){
        try {
            if (entity.getType() == EntityType.DROPPED_ITEM) {
                ItemMeta itemMeta = ((Item) entity).getItemStack().getItemMeta();
                if (itemMeta != null && UtilMethods.containsLocation(itemMeta.getDisplayName())) {
                    return true;
                }
            } else if (entity.getType() == EntityType.ARMOR_STAND) {
                if (UtilMethods.containsLocation(entity.getCustomName())) {
                    return true;
                }
            }
        } catch (NoSuchFieldError error){
            //do nothing
        }
        return false;
    }
}