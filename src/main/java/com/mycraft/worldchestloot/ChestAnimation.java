package com.mycraft.worldchestloot;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ChestAnimation {
    private static boolean warned;

    private ChestAnimation() { }

    static void play(MyCraftWorldChestLoot plugin, Player player, Block block, boolean open) {
        try {
            String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
            String version = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
            String nms = "net.minecraft.server." + version + ".";
            Class<?> blockPositionClass = Class.forName(nms + "BlockPosition");
            Class<?> nmsBlockClass = Class.forName(nms + "Block");
            Class<?> packetClass = Class.forName(nms + "PacketPlayOutBlockAction");
            Class<?> packetBaseClass = Class.forName(nms + "Packet");
            Object position = blockPositionClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(block.getX(), block.getY(), block.getZ());

            Class<?> magicNumbers = Class.forName("org.bukkit.craftbukkit." + version + ".util.CraftMagicNumbers");
            Object nmsBlock = magicNumbers.getMethod("getBlock", org.bukkit.Material.class).invoke(null, block.getType());
            Constructor<?> constructor = packetClass.getConstructor(blockPositionClass, nmsBlockClass, int.class, int.class);
            Object packet = constructor.newInstance(position, nmsBlock, 1, open ? 1 : 0);

            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Field connectionField = handle.getClass().getField("playerConnection");
            Object connection = connectionField.get(handle);
            Method sendPacket = connection.getClass().getMethod("sendPacket", packetBaseClass);
            sendPacket.invoke(connection, packet);
        } catch (Exception ex) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Could not play chest animation: " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage());
            }
        }
    }
}
