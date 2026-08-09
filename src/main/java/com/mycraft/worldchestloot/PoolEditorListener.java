package com.mycraft.worldchestloot;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class PoolEditorListener implements Listener {
    private static final int REWARD_SLOTS = 45;
    private static final int MANAGE_SLOT = 45;
    private static final int CHANCE_SLOT = 46;
    private static final int AMOUNT_SLOT = 47;
    private static final int RESET_SLOT = 49;
    private static final int SAVE_SLOT = 53;
    private final MyCraftWorldChestLoot plugin;

    PoolEditorListener(MyCraftWorldChestLoot plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PoolEditorHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        PoolEditorHolder holder = (PoolEditorHolder) top.getHolder();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= top.getSize()) {
            if (holder.getTool() != PoolEditorHolder.Tool.MANAGE) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (holder.getEntries().size() >= REWARD_SLOTS) {
                plugin.send(player, "editor-full");
                return;
            }
            holder.getEntries().add(new PoolEditorEntry(clicked, 100.0));
            holder.markDirty();
            refresh(plugin, holder);
            return;
        }

        if (rawSlot == MANAGE_SLOT) holder.setTool(PoolEditorHolder.Tool.MANAGE);
        else if (rawSlot == CHANCE_SLOT) holder.setTool(PoolEditorHolder.Tool.CHANCE);
        else if (rawSlot == AMOUNT_SLOT) holder.setTool(PoolEditorHolder.Tool.AMOUNT);
        else if (rawSlot == RESET_SLOT) editReset(holder, event.getClick());
        else if (rawSlot == SAVE_SLOT) player.closeInventory();
        else if (rawSlot >= 0 && rawSlot < holder.getEntries().size()) editEntry(holder, rawSlot, event.getClick());
        refresh(plugin, holder);
    }

    private void editReset(PoolEditorHolder holder, ClickType click) {
        if (click == ClickType.MIDDLE) { holder.toggleGlobalReset(); return; }
        int delta = click.isLeftClick() ? 3600 : click.isRightClick() ? -3600 : 0;
        if (click.isShiftClick()) delta *= 24;
        holder.changeCooldown(delta);
    }

    private void editEntry(PoolEditorHolder holder, int slot, ClickType click) {
        PoolEditorEntry entry = holder.getEntries().get(slot);
        if (holder.getTool() == PoolEditorHolder.Tool.MANAGE) {
            if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                holder.getEntries().remove(slot);
                holder.markDirty();
            }
            return;
        }
        double delta = click.isLeftClick() ? 1 : click.isRightClick() ? -1 : 0;
        if (click.isShiftClick()) delta *= 10;
        if (delta == 0) return;
        if (holder.getTool() == PoolEditorHolder.Tool.CHANCE) entry.changeChance(delta);
        else if (holder.getTool() == PoolEditorHolder.Tool.AMOUNT) entry.changeAmount((int) delta);
        holder.markDirty();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PoolEditorHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PoolEditorHolder)) return;
        PoolEditorHolder holder = (PoolEditorHolder) event.getInventory().getHolder();
        if (holder.isDirty()) {
            plugin.saveEditedPool(holder.getPoolName(), holder.getEntries(), holder.getCooldownSeconds(), holder.isGlobalReset());
            plugin.send(event.getPlayer(), "pool-saved", "<pool>", holder.getPoolName());
        }
    }

    static void refresh(MyCraftWorldChestLoot plugin, PoolEditorHolder holder) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) return;
        for (int slot = 0; slot < REWARD_SLOTS; slot++) inventory.clear(slot);
        for (int slot = 0; slot < holder.getEntries().size() && slot < REWARD_SLOTS; slot++) {
            PoolEditorEntry entry = holder.getEntries().get(slot);
            ItemStack display = entry.getItem();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(plugin.message("editor-divider"));
            lore.add(plugin.message("editor-chance", "<chance>", String.valueOf(entry.getChance())));
            lore.add(plugin.message("editor-amount", "<amount>", String.valueOf(entry.getAmount())));
            lore.add(plugin.message("editor-use-tool"));
            if (meta != null) { meta.setLore(lore); display.setItemMeta(meta); }
            inventory.setItem(slot, display);
        }
        inventory.setItem(MANAGE_SLOT, tool(plugin, Material.CHEST, plugin.message("tool-manage-name"), holder.getTool() == PoolEditorHolder.Tool.MANAGE,
                plugin.message("tool-manage-add"), plugin.message("tool-manage-remove")));
        inventory.setItem(CHANCE_SLOT, tool(plugin, Material.PAPER, plugin.message("tool-probability-name"), holder.getTool() == PoolEditorHolder.Tool.CHANCE,
                plugin.message("tool-probability-normal"), plugin.message("tool-probability-shift")));
        inventory.setItem(AMOUNT_SLOT, tool(plugin, Material.ANVIL, plugin.message("tool-amount-name"), holder.getTool() == PoolEditorHolder.Tool.AMOUNT,
                plugin.message("tool-amount-normal"), plugin.message("tool-amount-shift")));
        String resetName = plugin.message("tool-reset-name", "<time>", formatTime(plugin, holder.getCooldownSeconds()));
        String resetType = holder.isGlobalReset() ? plugin.message("reset-type-global") : plugin.message("reset-type-individual");
        inventory.setItem(RESET_SLOT, tool(plugin, Material.WATCH, resetName, false,
                plugin.message("tool-reset-type", "<type>", resetType), plugin.message("tool-reset-normal"),
                plugin.message("tool-reset-shift"), plugin.message("tool-reset-toggle")));
        inventory.setItem(SAVE_SLOT, tool(plugin, Material.EMERALD, plugin.message("tool-save-name"), false,
                plugin.message("tool-save-description")));
    }

    private static String formatTime(MyCraftWorldChestLoot plugin, int seconds) {
        int days = seconds / 86400;
        int hours = seconds % 86400 / 3600;
        int minutes = seconds % 3600 / 60;
        return plugin.message("editor-time", "<days>", String.valueOf(days), "<hours>", String.valueOf(hours),
                "<minutes>", String.valueOf(minutes));
    }

    private static ItemStack tool(MyCraftWorldChestLoot plugin, Material material, String name, boolean selected, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((selected ? ChatColor.GREEN : ChatColor.YELLOW) + name
                + (selected ? plugin.message("tool-selected-suffix") : ""));
        List<String> lore = new ArrayList<>();
        for (String line : lines) lore.add(ChatColor.GRAY + line);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
