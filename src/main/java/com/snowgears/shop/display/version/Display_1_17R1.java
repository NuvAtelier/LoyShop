package com.snowgears.shop.display.version;

import com.mojang.datafixers.util.Pair;
import com.snowgears.shop.Shop;
import com.snowgears.shop.display.AbstractDisplay;
import com.snowgears.shop.util.ArmorStandData;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Vector3f;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.level.World;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

public class Display_1_17R1 extends AbstractDisplay {

    public Display_1_17R1(Location shopSignLocation) {
        super(shopSignLocation);
        //printDirs();
    }

    @Override
    protected void spawnItemPacket(Player player, ItemStack is, Location location) {
        net.minecraft.world.item.ItemStack itemStack;
        try {
            Method asNMSCopy = Shop.getPlugin().getNmsBullshitHandler().getCraftItemStackClass().getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class);
            if (asNMSCopy != null) {
                itemStack = (net.minecraft.world.item.ItemStack) asNMSCopy.invoke(asNMSCopy.getClass(), is);
                if (itemStack != null) {
                    Object craftWorld = Shop.getPlugin().getNmsBullshitHandler().getCraftWorldClass().cast(location.getWorld());
                    if (craftWorld != null) {
                        Method serverWorld = craftWorld.getClass().getMethod("getHandle");
                        if (serverWorld != null) {
                            EntityItem entityItem = new EntityItem((World) serverWorld.invoke(craftWorld), location.getX(), location.getY(), location.getZ(), itemStack);
                            int entityID = entityItem.getId();
                            this.entityIDs.add(entityID);
                            entityItem.setInvulnerable(true);
                            entityItem.setFireTicks(-1);
                            entityItem.setNoGravity(true);
                            entityItem.persist = true;
                            entityItem.setMot(new Vec3D(0.0D, 0.0D, 0.0D));
                            entityItem.setPickupDelay(32767);
                            entityItem.setTicksFrozen(2147483647);

                            PacketPlayOutEntityDestroy entityDestroyPacket = new PacketPlayOutEntityDestroy(entityID);
                            PacketPlayOutSpawnEntity entitySpawnPacket = new PacketPlayOutSpawnEntity(entityItem);
                            PacketPlayOutEntityVelocity entityVelocityPacket = new PacketPlayOutEntityVelocity(entityItem);
                            PacketPlayOutEntityMetadata entityMetadataPacket = new PacketPlayOutEntityMetadata(entityID, entityItem.getDataWatcher(), true);

                            sendPacket(player, entityDestroyPacket);
                            sendPacket(player, entitySpawnPacket);
                            sendPacket(player, entityVelocityPacket);
                            sendPacket(player, entityMetadataPacket);
                        }
                    }
                }
            }
        } catch (NoSuchMethodException e){
            e.printStackTrace();
        } catch(InvocationTargetException e){
            e.printStackTrace();
        } catch(IllegalAccessException e){
            e.printStackTrace();
        }

    }

    @Override
    protected void spawnArmorStandPacket(Player player, ArmorStandData armorStandData, String text) {

        Location location = armorStandData.getLocation();
        WorldServer worldServer = ((CraftWorld) location.getWorld()).getHandle();

        EntityArmorStand armorStand = new EntityArmorStand(worldServer, location.getX(), location.getY(), location.getZ());
        armorStand.setLocation(location.getX(), location.getY(), location.getZ(), (float)armorStandData.getYaw(), 0);

        if(text != null) {
            armorStand.setCustomName(IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + text + "\"}"));
            this.addDisplayTag(player, armorStand.getId());
        }
        else{
            this.entityIDs.add(armorStand.getId());
        }
        armorStand.setCustomNameVisible(text != null);
        armorStand.setInvisible(true);

        if(armorStandData.getRightArmPose() != null){
            EulerAngle angle = armorStandData.getRightArmPose(); //EulerAngles are in radians
            float x = (float)Math.toDegrees(angle.getX());
            float y = (float)Math.toDegrees(angle.getY());
            float z = (float)Math.toDegrees(angle.getZ());
            armorStand.setRightArmPose(new Vector3f(x, y, z));
        }
        armorStand.setHeadRotation(0.0F);
        armorStand.setHeadPose(new Vector3f(0.0F, 0.0F, 0.0F));
        armorStand.setMarker(true);
        armorStand.setNoGravity(true);
        armorStand.setInvulnerable(true);
        armorStand.setInvisible(true);
        armorStand.persist = true;
        armorStand.collides = false;

        if(armorStandData.isSmall()) {
            armorStand.setSmall(true);
        }

        PacketPlayOutSpawnEntityLiving spawnEntityLivingPacket = new PacketPlayOutSpawnEntityLiving(armorStand);
        PacketPlayOutEntityMetadata spawnEntityMetadataPacket = new PacketPlayOutEntityMetadata(armorStand.getId(), armorStand.getDataWatcher(), true);
        PacketPlayOutEntityEquipment spawnEntityEquipmentPacket = null;

        //armor stand only going to have equipment if text is not populated
        if(text == null){
            ArrayList equipmentList = new ArrayList();
            equipmentList.add(new Pair(getEnumItemSlot(armorStandData.getEquipmentSlot()), CraftItemStack.asNMSCopy(armorStandData.getEquipment())));

            spawnEntityEquipmentPacket = new PacketPlayOutEntityEquipment(armorStand.getId(), equipmentList);
        }

        sendPacket(player, spawnEntityLivingPacket);
        sendPacket(player, spawnEntityMetadataPacket);
        if(spawnEntityEquipmentPacket != null){
            sendPacket(player, spawnEntityEquipmentPacket);
        }
    }

    @Override
    protected void spawnItemFramePacket(Player player, ItemStack is, Location location, BlockFace facing, boolean isGlowing){
        WorldServer worldServer = ((CraftWorld) location.getWorld()).getHandle();

        BlockPosition blockPosition = new BlockPosition(location.getX(), location.getY(), location.getZ());

        EntityItemFrame itemFrame;

        if(isGlowing){
            itemFrame = new GlowItemFrame(worldServer, blockPosition, getEnumDirection(facing));
        }
        else{
            itemFrame = new EntityItemFrame(worldServer, blockPosition, getEnumDirection(facing));
        }

        int entityID = itemFrame.getId();
        this.entityIDs.add(entityID);
        itemFrame.setLocation(location.getX(), location.getY(), location.getZ(),0f,0f);
        itemFrame.setItem(CraftItemStack.asNMSCopy(is));
        itemFrame.setDirection(getEnumDirection(facing));

        PacketPlayOutSpawnEntity entitySpawnPacket = new PacketPlayOutSpawnEntity(itemFrame, itemFrame.getDirection().b());
        PacketPlayOutEntityMetadata entityMetadataPacket = new PacketPlayOutEntityMetadata(entityID, itemFrame.getDataWatcher(), true);

        sendPacket(player, entitySpawnPacket);
        sendPacket(player, entityMetadataPacket);

    }

    private void sendPacket(Player player, Packet packet){
        if (player != null) {
            if(isSameWorld(player)) {
                PlayerConnection connection = getPlayerConnection(player);
                if (connection != null) {
                    connection.sendPacket(packet);
                }
            }
        }
        else {
            for (Player onlinePlayer : this.shopSignLocation.getWorld().getPlayers()) {
                PlayerConnection connection = getPlayerConnection(onlinePlayer);
                if(connection != null) {
                    connection.sendPacket(packet);
                }
            }
        }
    }

    @Override
    public void removeDisplayEntities(Player player, boolean onlyDisplayTags) {
        Iterator<Integer> entityIterator = this.getDisplayEntityIDIterator(player, onlyDisplayTags);
        if(entityIterator == null)
            return;

        while(entityIterator.hasNext()) {
            int displayEntityID = entityIterator.next();
            PacketPlayOutEntityDestroy destroyEntityPacket;
            try{
                destroyEntityPacket = new PacketPlayOutEntityDestroy(displayEntityID);
            } catch(NoSuchMethodError e){
                throw new RuntimeException("[Shop] [ERROR] This version of Shop does not support 1.17.0. Upgrade to 1.17.1.!");
            }
            sendPacket(player, destroyEntityPacket);
            entityIterator.remove();
        }
        if(onlyDisplayTags) {
            if(player != null && displayTagEntityIDs != null)
                displayTagEntityIDs.remove(player.getUniqueId());
        }
    }

    private PlayerConnection getPlayerConnection(Player player) {
        try {
            Object craftPlayer = Shop.getPlugin().getNmsBullshitHandler().getCraftPlayerClass().cast(player);
            if (craftPlayer != null) {
                Method getHandle = craftPlayer.getClass().getMethod("getHandle");
                if (getHandle != null) {
                    Object entityPlayer = getHandle.invoke(craftPlayer);
                    if (entityPlayer != null) {
                        Field playerConnection = entityPlayer.getClass().getField("b");
                        if(playerConnection != null)
                        return (PlayerConnection) playerConnection.get(entityPlayer);
                    }
                }
            }
        } catch(NoSuchMethodException e){
            e.printStackTrace();
        } catch(IllegalAccessException e){
            e.printStackTrace();
        } catch(InvocationTargetException e){
            e.printStackTrace();
        } catch (NoSuchFieldException e){
            e.printStackTrace();
        }
        return null;
    }

    private EnumDirection getEnumDirection(BlockFace facing){
        switch (facing){
            case NORTH:
                return EnumDirection.c;
            case SOUTH:
                return EnumDirection.d;
            case WEST:
                return EnumDirection.e;
            case EAST:
                return EnumDirection.f;
            case DOWN:
                return EnumDirection.a;
            default:
                return EnumDirection.b;
        }
    }

    private EnumItemSlot getEnumItemSlot(EquipmentSlot equipmentSlot){
        switch(equipmentSlot){
            case HAND:
                return EnumItemSlot.a;
            case OFF_HAND:
                return EnumItemSlot.b;
            case FEET:
                return EnumItemSlot.c;
            case LEGS:
                return EnumItemSlot.d;
            case CHEST:
                return EnumItemSlot.e;
            default:
                return EnumItemSlot.f;
        }
    }
}
