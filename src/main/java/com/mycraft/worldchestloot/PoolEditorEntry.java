package com.mycraft.worldchestloot;

import org.bukkit.inventory.ItemStack;

final class PoolEditorEntry {
    private final ItemStack item;
    private double chance;

    PoolEditorEntry(ItemStack item, double chance) {
        this.item = item.clone();
        this.chance = normalize(chance);
    }

    ItemStack getItem() { return item.clone(); }
    int getAmount() { return item.getAmount(); }
    double getChance() { return chance; }

    void changeChance(double delta) { chance = normalize(chance + delta); }

    void changeAmount(int delta) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), item.getAmount() + delta)));
    }

    private double normalize(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
