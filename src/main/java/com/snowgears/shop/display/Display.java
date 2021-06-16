package com.snowgears.shop.display;

import com.snowgears.shop.AbstractShop;
import com.snowgears.shop.Shop;
import com.snowgears.shop.ShopType;
import com.snowgears.shop.util.DisplayUtil;
import com.snowgears.shop.util.ShopMessage;
import com.snowgears.shop.util.UtilMethods;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;

public class Display {

    private Location shopSignLocation;
    private DisplayType type;
    private ArrayList<Entity> entities;
    private DisplayType[] cycle = Shop.getPlugin().getDisplayCycle();
    private boolean nameTagsVisible;
    private int chunkX;
    private int chunkZ;

    public Display(Location shopSignLocation) {
        this.shopSignLocation = shopSignLocation;
        entities = new ArrayList<>();

        chunkX = UtilMethods.floor(shopSignLocation.getBlockX()) >> 4;
        chunkZ = UtilMethods.floor(shopSignLocation.getBlockZ()) >> 4;
    }

    public boolean isInChunk(Chunk chunk){
        return chunk.getX() == chunkX && chunk.getZ() == chunkZ && chunk.getWorld().toString().equals(shopSignLocation.getWorld().toString());
    }

    public void spawn(boolean removeOld) {
        if(removeOld)
            remove();
        //Random random = new Random();

        AbstractShop shop = this.getShop();

        if (shop.getItemStack() == null)
            return;

        //define the initial display item
        ItemStack item = shop.getItemStack().clone();
        item.setAmount(1);

        DisplayType displayType = this.type;
        if(displayType == null)
            displayType = Shop.getPlugin().getDisplayType();

        //two display entities on the chest
        if (shop.getType() == ShopType.BARTER) {
            if (shop.getSecondaryItemStack() == null)
                return;

            //define the barter display item
            ItemStack barterItem = shop.getSecondaryItemStack().clone();
            barterItem.setAmount(1);

            switch (displayType){
                case NONE:
                    //do nothing
                    break;
                case ITEM:
                    //Drop initial display item
                    Item i1 = shop.getChestLocation().getWorld().dropItem(this.getItemDropLocation(false), item);
                    i1.setVelocity(new Vector(0, 0.1, 0));
                    i1.setPickupDelay(Integer.MAX_VALUE); //stop item from being picked up ever
                    //tagDisplayWithName(i1, item);
                    tagEntityAsDisplay(i1);

                    //Drop the barter display item
                    Item i2 = shop.getChestLocation().getWorld().dropItem(this.getItemDropLocation(true), barterItem);
                    i2.setVelocity(new Vector(0, 0.1, 0));
                    i2.setPickupDelay(Integer.MAX_VALUE); //stop item from being picked up ever
                    //tagDisplayWithName(i2, barterItem);
                    tagEntityAsDisplay(i2);
                    break;
                case LARGE_ITEM:
                    //put first large display down
                    Location leftLoc = shop.getChestLocation().getBlock().getRelative(BlockFace.UP).getLocation();
                    leftLoc.add(getLargeItemBarterOffset(false));
                    ArmorStand stand = DisplayUtil.createDisplay(item, leftLoc, shop.getFacing());
                    //tagDisplayWithName(stand, item);
                    tagEntityAsDisplay(stand);

                    //put second large display down
                    Location rightLoc = shop.getChestLocation().getBlock().getRelative(BlockFace.UP).getLocation();
                    rightLoc.add(getLargeItemBarterOffset(true));
                    ArmorStand stand2 = DisplayUtil.createDisplay(barterItem, rightLoc, shop.getFacing());
                    //tagDisplayWithName(stand2, barterItem);
                    tagEntityAsDisplay(stand2);
                    break;
                case GLASS_CASE:
                    //put the extra large glass casing down
                    Location caseLoc = shop.getChestLocation().getBlock().getRelative(BlockFace.UP).getLocation();
                    caseLoc.add(0, -0.74, 0);
                    ArmorStand caseStand = DisplayUtil.createDisplay(new ItemStack(Material.GLASS), caseLoc, shop.getFacing());
                    caseStand.setSmall(false);
                    tagEntityAsDisplay(caseStand);

                    //Drop initial display item
                    Item item1 = shop.getChestLocation().getWorld().dropItem(this.getItemDropLocation(false), item);
                    item1.setVelocity(new Vector(0, 0.1, 0));
                    item1.setPickupDelay(Integer.MAX_VALUE); //stop item from being picked up ever
                    //tagDisplayWithName(item1, item);
                    tagEntityAsDisplay(item1);

                    //Drop the barter display item
                    Item item2 = shop.getChestLocation().getWorld().dropItem(this.getItemDropLocation(true), barterItem);
                    item2.setVelocity(new Vector(0, 0.1, 0));
                    item2.setPickupDelay(Integer.MAX_VALUE); //stop item from being picked up ever
                    //tagDisplayWithName(item2, barterItem);
                    tagEntityAsDisplay(item2);
                    break;
                    //this code stacks item frames on top of each other for barter type. i dont like it and will be disabling item_frame display type for barter shops for the moment
//                case ITEM_FRAME:
//                    ItemFrame frame = (ItemFrame) shop.getChestLocation().getWorld().spawn(shop.getChestLocation().getBlock().getLocation().clone().add(0, 2, 0),
//                            ItemFrame.class,
//                            entity -> {
//                                ItemFrame itemFrame = (ItemFrame) entity;
//                                itemFrame.setFacingDirection(shop.getFacing(), true);
//                                itemFrame.setFixed(true);
//                                itemFrame.setCustomName(this.generateDisplayName(random));
//                                itemFrame.setItem(item);
//                            });
//                    ItemFrame frame2 = (ItemFrame) shop.getChestLocation().getWorld().spawn(shop.getChestLocation().getBlock().getLocation().clone().add(0, 1, 0),
//                            ItemFrame.class,
//                            entity -> {
//                                ItemFrame itemFrame = (ItemFrame) entity;
//                                itemFrame.setFacingDirection(shop.getFacing(), true);
//                                itemFrame.setFixed(true);
//                                itemFrame.setCustomName(this.generateDisplayName(random));
//                                itemFrame.setItem(barterItem);
//                            });
//                    entities.add(frame);
//                    entities.add(frame2);
//                    break;
            }
        }
        //one display entity on the chest
        else {
            switch (displayType){
                case NONE:
                    //do nothing
                    break;
                case ITEM:
                    Item i = shop.getChestLocation().getWorld().dropItem(this.getItemDropLocation(false), item);
                    i.setVelocity(new Vector(0, 0.1, 0));
                    i.setPickupDelay(Integer.MAX_VALUE); //stop item from being picked up ever
                    //tagDisplayWithName(i, item);
                    tagEntityAsDisplay(i);
                    break;
                case LARGE_ITEM:
                    ArmorStand stand = DisplayUtil.createDisplay(item, shop.getChestLocation().getBlock().getRelative(BlockFace.UP).getLocation(), shop.getFacing());
                    //tagDisplayWithName(stand, item);
                    tagEntityAsDisplay(stand);
                    break;
                case GLASS_CASE:
                    //put the extra large glass casing down
                    Location caseLoc = shop.getChestLocation().getBlock().getRelative(BlockFace.UP).getLocation();
                    caseLoc.add(0, -0.74, 0);
                    ArmorStand caseStand = DisplayUtil.createDisplay(new ItemStack(Material.GLASS), caseLoc, shop.getFacing());
                    caseStand.setSmall(false);
                    tagEntityAsDisplay(caseStand);

                    //drop the display item in the glass case
                    Item caseDisplayItem = shop.getChestLocation().getWorld().dropItem(this.getItemDropLocation(false), item);
                    caseDisplayItem.setVelocity(new Vector(0, 0.1, 0));
                    caseDisplayItem.setPickupDelay(Integer.MAX_VALUE); //stop item from being picked up ever
                    //tagDisplayWithName(caseDisplayItem, item);
                    tagEntityAsDisplay(caseDisplayItem);
                    break;
                case ITEM_FRAME:
                    Block aboveShop = this.getShop().getChestLocation().getBlock().getRelative(BlockFace.UP);
                    Location frameLocation = aboveShop.getLocation();
                    //if display is blocked, put item frame on front
                    if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                        frameLocation = aboveShop.getRelative(shop.getFacing()).getLocation();
                    }
                    if(Shop.getPlugin().getGlowingItemFrame()){
                        GlowItemFrame frame = (GlowItemFrame) shop.getChestLocation().getWorld().spawn(frameLocation,
                                GlowItemFrame.class,
                                entity -> {
                                    GlowItemFrame itemFrame = (GlowItemFrame) entity;
                                    itemFrame.setFacingDirection(shop.getFacing(), true);
                                    itemFrame.setFixed(true);
                                    itemFrame.setItem(shop.getItemStack());
                                });
                        tagEntityAsDisplay(frame);
                    }
                    else {
                        ItemFrame frame = (ItemFrame) shop.getChestLocation().getWorld().spawn(frameLocation,
                                ItemFrame.class,
                                entity -> {
                                    ItemFrame itemFrame = (ItemFrame) entity;
                                    itemFrame.setFacingDirection(shop.getFacing(), true);
                                    itemFrame.setFixed(true);
                                    itemFrame.setItem(shop.getItemStack());
                                });
                        tagEntityAsDisplay(frame);
                    }
                    break;
            }
        }
        shop.updateSign();
    }

    public void showNameTags(){
        if(nameTagsVisible || !getShop().isInitialized()) {
            return;
        }

        nameTagsVisible = true;

        try {
            ArrayList<String> displayTags = ShopMessage.getDisplayTags(getShop(), getShop().getType());

            Location lowerTagLocation = getShop().getChestLocation().getBlock().getRelative(BlockFace.UP).getLocation();
            lowerTagLocation = lowerTagLocation.add(0.5, -.7, 0.5);

            double verticalAddition = 0;
            //iterate through list backwards to build from bottom -> up
            for (int i = displayTags.size() - 1; i >= 0; i--) {
                Location asTagLocation = lowerTagLocation.clone();

                String tagLine = displayTags.get(i);
                if (tagLine.contains("[lshift]")) {
                    asTagLocation = asTagLocation.add(getLargeItemBarterOffset(false));
                    asTagLocation = asTagLocation.add(getLargeItemBarterOffset(false));
                    tagLine = tagLine.replace("[lshift]", "");
                }
                if (tagLine.contains("[rshift]")) {
                    asTagLocation = asTagLocation.add(getLargeItemBarterOffset(true));
                    asTagLocation = asTagLocation.add(getLargeItemBarterOffset(true));
                    tagLine = tagLine.replace("[rshift]", "");
                }

                asTagLocation = asTagLocation.add(0, verticalAddition, 0);
                createTagEntity(tagLine, asTagLocation);
                verticalAddition += 0.3; //TODO need to play around with this value a bit
            }

            //remove all armor stand name tag entities after x seconds (10 default)
            new BukkitRunnable() {
                @Override
                public void run() {
                    removeTagEntities();
                }
            }.runTaskLater(Shop.getPlugin(), (Shop.getPlugin().getDisplayTagLifespan() * 20));
        } catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    public void removeTagEntities() {
        Iterator<Entity> entityIterator = entities.iterator();
        while (entityIterator.hasNext()) {
            Entity entity = entityIterator.next();
            if (entity != null && entity.getType() == EntityType.ARMOR_STAND) {
                PersistentDataContainer persistentData = entity.getPersistentDataContainer();
                if (persistentData != null) {
                    try {
                        int dataDisplay = persistentData.get(new NamespacedKey(Shop.getPlugin(), "display_nametag"), PersistentDataType.INTEGER);
                        if (dataDisplay == 1) {
                            entityIterator.remove();
                            entity.remove();
                        }
                    } catch (NullPointerException e) {
                    }
                }
            }
        }
        nameTagsVisible = false;
    }


    private void createTagEntity(String text, Location location){
        ArmorStand as = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND); //Spawn the ArmorStand

        as.setGravity(false);
        as.setCanPickupItems(false);
        as.setCustomName(text);
        as.setCustomNameVisible(true);
        as.setVisible(false);
        as.setInvulnerable(true);
        as.setSmall(true);
        tagEntityAsDisplay(as);

        PersistentDataContainer persistentData = as.getPersistentDataContainer();
        persistentData.set(new NamespacedKey(Shop.getPlugin(), "display_nametag"), PersistentDataType.INTEGER, 1);
    }

    private void tagEntityAsDisplay(Entity entity){
        //give player a limited amount of time to finish creating the shop until it is deleted
        Bukkit.getScheduler().scheduleSyncDelayedTask(Shop.getPlugin(), new Runnable() {
            public void run() {
                if(entity != null && !entity.isDead()) {
                    PersistentDataContainer persistentData = entity.getPersistentDataContainer();
                    persistentData.set(new NamespacedKey(Shop.getPlugin(), "display"), PersistentDataType.INTEGER, 1);
                    persistentData.set(new NamespacedKey(Shop.getPlugin(), "signlocation"), PersistentDataType.STRING, UtilMethods.getCleanLocation(shopSignLocation, false));
                    entity.setPersistent(true);
                }
            }
        }, 2); //2 ticks
        entities.add(entity);
    }

    public DisplayType getType(){
        return type;
    }

    public AbstractShop getShop(){
        return Shop.getPlugin().getShopHandler().getShop(this.shopSignLocation);
    }

    public void setType(DisplayType type){
        DisplayType oldType = this.type;

        if((oldType == DisplayType.NONE && type != DisplayType.ITEM_FRAME) || (oldType == DisplayType.ITEM_FRAME && type != DisplayType.NONE)){
            //make sure there is room above the shop for the display
            Block aboveShop = this.getShop().getChestLocation().getBlock().getRelative(BlockFace.UP);
            if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                return;
            }
        }

        this.type = type;
        this.spawn(true);
    }

    public void cycleType(){
        DisplayType displayType = this.type;
        if(displayType == null) {
            displayType = Shop.getPlugin().getDisplayType();
        }

        int index = -1;
        if(displayType == DisplayType.NONE){
            //make sure there is room above the shop for the display
            Block aboveShop = this.getShop().getChestLocation().getBlock().getRelative(BlockFace.UP);
            if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                //if the cycle contains the ITEM_FRAME display type
                for(int i=0; i<cycle.length; i++){
                    if(cycle[i] == DisplayType.ITEM_FRAME){
                        index = i;
                    }
                }
                //there is no ITEM_FRAME in cycle, return because display is blocked
                if(index == -1)
                    return;
            }
        }
        else if(displayType == DisplayType.ITEM_FRAME){
            //make sure there is room above the shop for the display
            Block aboveShop = this.getShop().getChestLocation().getBlock().getRelative(BlockFace.UP);
            if (!UtilMethods.materialIsNonIntrusive(aboveShop.getType())) {
                //if the cycle contains the NONE display type
                for(int i=0; i<cycle.length; i++){
                    if(cycle[i] == DisplayType.NONE){
                        index = i;
                    }
                }
                //there is no NONE in cycle, return because display is blocked
                if(index == -1)
                    return;
            }
        }

        //index is still not set, continue and cycle index to next display type
        if(index == -1) {
            index = 0;
            for (int i = 0; i < cycle.length; i++) {
                if (cycle[i] == displayType)
                    index = i + 1;
            }
            if (index >= cycle.length)
                index = 0;
        }

        //don't allow barter shops to have ITEM_FRAME display types (for NOW)
        if(cycle[index] == DisplayType.ITEM_FRAME){

            boolean skip = false;
            if(getShop().getType() == ShopType.BARTER){
                skip = true;
            }
            else {
                //calculate where ITEM_FRAME display may be
                for(Entity e : this.getShop().getChestLocation().getWorld().getNearbyEntities(this.getItemDropLocation(false), 1, 1, 1)){
                    if(e.getType() == EntityType.ITEM_FRAME){
                        ItemFrame i = (ItemFrame)e;
                        if(i.getAttachedFace() == getShop().getSign().getFacing().getOppositeFace()) {
                            skip = true;
                            break;
                        }
                    }
                }
            }

            if(skip) {
                index++;
                if (index >= cycle.length)
                    index = 0;
            }
        }

        this.setType(cycle[index]);

        Shop.getPlugin().getShopHandler().saveShops(getShop().getOwnerUUID());
    }

    public void remove() {
        AbstractShop shop = this.getShop();

        Iterator<Entity> displayIterator = entities.iterator();
        while(displayIterator.hasNext()) {
            Entity displayEntity = displayIterator.next();
            displayEntity.remove();
        }
        entities.clear();

        for (Entity entity : shop.getChestLocation().getChunk().getEntities()) {
            if(isDisplay(entity)){
                AbstractShop s =  getShop(entity);
                //remove any displays that are left over but still belong to the same shop
                if(s != null && s.getSignLocation().equals(shop.getSignLocation()))
                    entity.remove();
            }
        }
    }

    private Location getItemDropLocation(boolean isBarterItem) {
        AbstractShop shop = this.getShop();

        //calculate which x,z to drop items at depending on direction of the shop sign
        double dropY = 1.2;
        double dropX = 0.5;
        double dropZ = 0.5;
        if (shop.getType() == ShopType.BARTER) {
            WallSign shopSign = (WallSign) shop.getSignLocation().getBlock().getBlockData();
            switch (shopSign.getFacing()) {
                case NORTH:
                    if (isBarterItem)
                        dropX = 0.3;
                    else
                        dropX = 0.7;
                    break;
                case EAST:
                    if (isBarterItem)
                        dropZ = 0.3;
                    else
                        dropZ = 0.7;
                    break;
                case SOUTH:
                    if (isBarterItem)
                        dropX = 0.7;
                    else
                        dropX = 0.3;
                    break;
                case WEST:
                    if (isBarterItem)
                        dropZ = 0.7;
                    else
                        dropZ = 0.3;
                    break;
                default:
                    dropX = 0.5;
                    dropZ = 0.5;
                    break;
            }
        }
        return shop.getChestLocation().clone().add(dropX, dropY, dropZ);
    }

    private Vector getLargeItemBarterOffset(boolean isBarterItem){
        AbstractShop shop = this.getShop();

        Vector offset = new Vector(0,0,0);
        double space = 0.24;
        if (shop.getType() == ShopType.BARTER) {
            WallSign shopSign = (WallSign) shop.getSignLocation().getBlock().getBlockData();
            switch (shopSign.getFacing()) {
                case NORTH:
                    if (isBarterItem)
                        offset.setX(-space);
                    else
                        offset.setX(space);
                    break;
                case EAST:
                    if (isBarterItem)
                        offset.setZ(-space);
                    else
                        offset.setZ(space);
                    break;
                case SOUTH:
                    if (isBarterItem)
                        offset.setX(space);
                    else
                        offset.setX(-space);
                    break;
                case WEST:
                    if (isBarterItem)
                        offset.setZ(space);
                    else
                        offset.setZ(-space);
                    break;
            }
        }
        return offset;
    }

    public static boolean isDisplay(Entity entity){
        PersistentDataContainer persistentData = entity.getPersistentDataContainer();
        if(persistentData != null) {
            try {
                int dataDisplay = persistentData.get(new NamespacedKey(Shop.getPlugin(), "display"), PersistentDataType.INTEGER);
                return (dataDisplay == 1);
            } catch (NullPointerException e){ return false; }
        }
        return false;
    }

    public static AbstractShop getShop(Entity display){
        //use persistent api
        PersistentDataContainer persistentData = display.getPersistentDataContainer();
        if(persistentData != null) {
            try {
                String dataDisplay = persistentData.get(new NamespacedKey(Shop.getPlugin(), "signlocation"), PersistentDataType.STRING);
                if(dataDisplay != null){
                    Location signLocation = UtilMethods.getLocation(dataDisplay);
                    if(signLocation != null)
                        return Shop.getPlugin().getShopHandler().getShop(signLocation);
                }

            } catch (NullPointerException e){ }
        }
        return null;
    }
}