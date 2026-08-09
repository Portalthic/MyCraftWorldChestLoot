package com.mycraft.worldchestloot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

final class PoolEditorHolder implements InventoryHolder {
    enum Tool { MANAGE, CHANCE, AMOUNT }

    private final String poolName;
    private final List<PoolEditorEntry> entries = new ArrayList<>();
    private Tool tool = Tool.MANAGE;
    private Inventory inventory;
    private int cooldownSeconds;
    private boolean globalReset;
    private boolean dirty;

    PoolEditorHolder(String poolName, int cooldownSeconds, boolean globalReset) {
        this.poolName = poolName;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.globalReset = globalReset;
    }

    String getPoolName() { return poolName; }
    List<PoolEditorEntry> getEntries() { return entries; }
    Tool getTool() { return tool; }
    void setTool(Tool tool) { this.tool = tool; }
    void setInventory(Inventory inventory) { this.inventory = inventory; }
    int getCooldownSeconds() { return cooldownSeconds; }
    boolean isGlobalReset() { return globalReset; }
    boolean isDirty() { return dirty; }
    void markDirty() { dirty = true; }
    void changeCooldown(int seconds) { cooldownSeconds = Math.max(0, cooldownSeconds + seconds); dirty = true; }
    void toggleGlobalReset() { globalReset = !globalReset; dirty = true; }

    @Override public Inventory getInventory() { return inventory; }
}
