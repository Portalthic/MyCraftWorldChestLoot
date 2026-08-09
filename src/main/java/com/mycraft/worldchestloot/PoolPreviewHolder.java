package com.mycraft.worldchestloot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

final class PoolPreviewHolder implements InventoryHolder {
    private final List<PoolEditorEntry> entries = new ArrayList<>();
    private Inventory inventory;

    List<PoolEditorEntry> getEntries() { return entries; }
    void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
