package com.mycraft.worldchestloot;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ChestLootListener implements Listener {
    private final MyCraftWorldChestLoot plugin;
    ChestLootListener(MyCraftWorldChestLoot plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !plugin.isChest(block.getType())) return;
        block = plugin.canonicalChest(block);
        MyCraftWorldChestLoot.ResolvedLink link = plugin.resolveLink(block.getLocation());
        if (link == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        plugin.openLoot(player, block, link);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            plugin.closeVirtualChest((Player) event.getPlayer());
        }
    }
}
