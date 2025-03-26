package com.snowgears.shop.util;

import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;

public class ItemNameUtil {

    private Map<String, String> names = new HashMap<String, String>();

    public ItemNameUtil() { }

    public String translate(String key){
        return new TranslatableComponent(key).toPlainText();
    }

    public TextComponent getName(ItemStack item){
        if(item == null)
            return new TextComponent("");


        // Check if there is a name embedded in the item, aka named by an anvil or command
        if(item.getItemMeta() != null && item.getItemMeta().getDisplayName() != null && !item.getItemMeta().getDisplayName().isEmpty()){
            return new TextComponent(item.getItemMeta().getDisplayName());
        }

        // Add custom formatting for player heads
        if(item.getItemMeta() != null && item.getItemMeta() instanceof SkullMeta){
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta.getOwningPlayer() != null) {
                return new TextComponent(skullMeta.getOwnerProfile().getName() + "'s Head");
            }
        }

        // Fallback to the material name
        return getNameTranslatable(item.getType());
    }

    public TextComponent getNameTranslatable(Material material){
        return new TextComponent(new TranslatableComponent(material.getTranslationKey()));
    }
}
