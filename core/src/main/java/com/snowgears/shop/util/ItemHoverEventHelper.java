package com.snowgears.shop.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Content;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.util.Objects;

/**
 * Helper to create a Bungee HoverEvent (show_item) using modern item components (1.20.5+),
 * relying only on Paper API. Produces inlined {id,count,components} payload.
 */
public final class ItemHoverEventHelper {

    private ItemHoverEventHelper() {}

    public static HoverEvent createFrom(final ItemStack bukkitItem) {
        // If we are 1.20.5+, then we use the new `components` field instead of the old `nbt` Tag system
        try {
            JsonObject obj = Bukkit.getUnsafe().serializeItemAsJson(bukkitItem);
            if (obj != null) {
                final String id = obj.get("id").getAsString();
                final int count = bukkitItem.getAmount();   
                final JsonElement components = obj.get("components");
                return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ComponentsShowItem(id, count, components));
            }
        } catch (Error | Exception ignored) {
            // Paper-specific API not available; fall back to legacy tag path below
        }

        // Fallback to the old `nbt` Tag system if we are below 1.20.5
        final String id = bukkitItem.getType().getKey().toString();
        final int count = bukkitItem.getAmount();
        String nbt = null;
        try {
            final org.bukkit.inventory.meta.ItemMeta meta = bukkitItem.getItemMeta();
            if (meta != null) {
                final java.lang.reflect.Method gm = meta.getClass().getMethod("getAsString");
                final Object res = gm.invoke(meta);
                if (res != null) nbt = res.toString();
            }
        } catch (Exception ignored) {
            // Some servers may not expose ItemMeta#getAsString; continue with null nbt
            // maybe fallback to Item NBT API again someday if needed (look through old commits)
        }
        final net.md_5.bungee.api.chat.ItemTag tag = (nbt != null && !nbt.isEmpty()) ? net.md_5.bungee.api.chat.ItemTag.ofNbt(nbt) : null;
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new net.md_5.bungee.api.chat.hover.content.Item(id, count, tag));
    }

    /** Minimal show_item content carrying components for modern clients. */
    private static final class ComponentsShowItem extends Content {
        private final String id;
        private final int count;
        private final JsonElement components;

        private ComponentsShowItem(final String id, final int count, final JsonElement components) {
            this.id = id;
            this.count = count;
            this.components = components;
        }

        @Override
        public HoverEvent.Action requiredAction() {
            return HoverEvent.Action.SHOW_ITEM;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComponentsShowItem that = (ComponentsShowItem) o;
            return count == that.count && Objects.equals(id, that.id) && Objects.equals(components, that.components);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, count, components);
        }
    }
}


