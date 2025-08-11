package com.snowgears.shop.util;


import com.snowgears.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.*;

public class InventoryUtils {

    //removes itemstack from inventory
    //returns the amount of items it could not remove
    public static int removeItem(Inventory inventory, ItemStack itemStack) {
        if(inventory == null || itemStack.getAmount() >= (27 * 64)) // 27 stacks max, large values > 27 stacks can crash server!
            return itemStack.getAmount();
        if (itemStack == null || itemStack.getAmount() <= 0)
            return 0;

        ItemStack[] contents = inventory.getContents();
        int amount = itemStack.getAmount();
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is != null) {
                // Check if we are the same item type
                if (itemstacksAreSimilar(is, itemStack)) {
                    // Take items from stack
                    if (is.getAmount() > amount) {
                        contents[i].setAmount(is.getAmount() - amount);
                        inventory.setContents(contents);
                        return 0;
                    }
                    // If we are equal, remove the stack from the inventory
                    else if (is.getAmount() == amount) {
                        contents[i].setType(Material.AIR);
                        inventory.setContents(contents);
                        return 0;
                    }
                    // We have less than enough, take the amount
                    else {
                        amount -= is.getAmount();
                        contents[i].setType(Material.AIR);
                    }
                }
            }
        }
        inventory.setContents(contents);
        return amount;
    }

    //takes an ItemStack and splits it up into multiple ItemStacks with correct stack sizes
    //then adds those items to the given inventory
    public static int addItem(Inventory inventory, ItemStack itemStack) {
        if(inventory == null || itemStack.getAmount() >= (27 * 64)) // 27 stacks max, large values > 27 stacks can crash server!
            return itemStack.getAmount();
        if (itemStack.getAmount() <= 0)
            return 0;
        ArrayList<ItemStack> itemStacksAdding = new ArrayList<ItemStack>();

        //break up the itemstack into multiple ItemStacks with correct stack size
        int fullStacks = itemStack.getAmount() / itemStack.getMaxStackSize();
        int partialStack = itemStack.getAmount() % itemStack.getMaxStackSize();
        for (int i = 0; i < fullStacks; i++) {
            ItemStack is = itemStack.clone();
            is.setAmount(is.getMaxStackSize());
            itemStacksAdding.add(is);
        }
        ItemStack is = itemStack.clone();
        is.setAmount(partialStack);
        if (partialStack > 0)
            itemStacksAdding.add(is);

        //try adding all items from itemStacksAdding and return number of ones you couldnt add
        int amount = 0;
        for (ItemStack addItem : itemStacksAdding) {
            HashMap<Integer, ItemStack> noAdd = inventory.addItem(addItem);
            for(ItemStack noAddItemstack : noAdd.values()) {
                amount += noAddItemstack.getAmount();
            }
        }
        return amount;
    }

    public static boolean hasRoom(Inventory inventory, ItemStack itemStack) {
        if (inventory == null || itemStack.getAmount() >= (27 * 64)) // 27 stacks max, large values > 27 stacks can crash server!
            return false;
        if (itemStack.getAmount() <= 0)
            return true;

        // Check a cloned inventory instead of modifying the existing inventory
        Inventory clonedInv = getVirtualInventory(inventory);

        // Check if we can successfully add all the items to the players inventory
        int itemsLeftToAdd = addItem(clonedInv, itemStack);
        if (itemsLeftToAdd > 0) {
            return false;
        }
        return true;
    }

    public static Inventory getVirtualInventory(Inventory inventory) {
        // Check a cloned inventory instead of manipulating the original inventory
        Inventory clonedInv = Bukkit.createInventory(null, inventory.getStorageContents().length);
        clonedInv.setContents(inventory.getStorageContents());

        return clonedInv;
    }

    //gets the amount of items in inventory
    public static int getAmount(Inventory inventory, ItemStack itemStack){
        if(inventory == null)
            return 0;
        ItemStack[] contents = inventory.getStorageContents();
        int amount = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is != null) {
                if (itemstacksAreSimilar(itemStack, is)) {
                    amount += is.getAmount();
                }
            }
        }
        return amount;
    }

    public static boolean itemstacksAreSimilar(ItemStack i1, ItemStack i2){
        if(i1 == null || i2 == null)
            return false;
        if(i1.getType() != i2.getType())
            return false;

        ItemStack itemStack1 = i1.clone();
        ItemStack itemStack2 = i2.clone();

        // Check if we are ignoring item durability, if so, reset the durability of both items and continue with later checks
        if (!Shop.getPlugin().checkItemDurability()) {
            Damageable is1Damagable = (Damageable) itemStack1.getItemMeta();
            is1Damagable.setDamage(0);

            Damageable is2Damagable = (Damageable) itemStack2.getItemMeta();
            is2Damagable.setDamage(0);

            itemStack1.setItemMeta(is1Damagable);
            itemStack2.setItemMeta(is2Damagable);
        }

        // Check if we are ignoring item durability, if so, reset the durability of both items and continue with later checks
        if (Shop.getPlugin().ignoreItemRepairCost()) {
            Repairable item1Cost = (Repairable) itemStack1.getItemMeta();
            item1Cost.setRepairCost(0);

            Repairable item2Cost = (Repairable) itemStack2.getItemMeta();
            item2Cost.setRepairCost(0);

            itemStack1.setItemMeta(item1Cost);
            itemStack2.setItemMeta(item2Cost);
        }

        ItemMeta i1Meta = itemStack1.getItemMeta();
        ItemMeta i2Meta = itemStack2.getItemMeta();


        // Check if shulker box contents are identical
        if(itemStack1.getType().toString().toLowerCase().contains("shulker_box")){
            if (!itemStack2.getType().toString().toLowerCase().contains("shulker_box")) return false;

            // Note: You must reference i1 and i2 here, if you do not then both inventories are identical for some reason... Do not reference the cloned item stacks here...
            BlockStateMeta bsm1 = (BlockStateMeta) i1.getItemMeta();
            BlockStateMeta bsm2 = (BlockStateMeta) i2.getItemMeta();
            Inventory inv1 = ((ShulkerBox) bsm1.getBlockState()).getInventory();
            Inventory inv2 = ((ShulkerBox) bsm2.getBlockState()).getInventory();

            ItemStack[] inv1Contents = inv1.getContents();
            ItemStack[] inv2Contents = inv2.getContents();

            for (int i = 0; i < inv1Contents.length; i++) {
                ItemStack inv1Item = inv1Contents[i];
                ItemStack inv2Item = inv2Contents[i];

                if (inv1Item == null && inv2Item == null) continue;
                if (inv1Item == null && inv2Item != null) return false;
                if (inv1Item != null && inv2Item == null) return false;
                if (!itemstacksAreSimilar(inv1Item, inv2Item)) return false;
            }
        }


        //fix NBT attributes for cached older items to be compatible with Spigot serializer updates
        if (i1Meta != null && i2Meta != null && i1Meta.hasAttributeModifiers() && i2Meta.hasAttributeModifiers()) {
            i1Meta.setAttributeModifiers(i1Meta.getAttributeModifiers());
            i2Meta.setAttributeModifiers(i2Meta.getAttributeModifiers());
            itemStack1.setItemMeta(i1Meta);
            itemStack2.setItemMeta(i2Meta);
        }

        return itemStack1.isSimilar(itemStack2);
    }

    public static boolean isEmpty(Inventory inv){
        if(inv == null)
            return true;
        for(ItemStack it : inv.getContents())
        {
            if(it != null)
                return false;
        }
        return true;
    }

    public static ItemStack getRandomItem(Inventory inv){
        if(inv == null)
            return null;
        ArrayList<ItemStack> contents = new ArrayList<>();
        for(ItemStack it : inv.getContents())
        {
            if(it != null){
                contents.add(it);
            }

        }
        if(contents.size() == 0)
            return null;
        Collections.shuffle(contents);

        int index = new Random().nextInt(contents.size());
        return contents.get(index);
    }
}
