package com.mycraft.worldchestloot;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public final class Reward {
    public enum Type { ITEM, ZAPHKIEL }
    private final Type type;
    private final ItemStack template;
    private final String id;
    private final int amount;
    private final double chance;

    public Reward(Type type, ItemStack template, String id, int amount, double chance) {
        this.type = type; this.template = template; this.id = id;
        this.amount = Math.max(1, amount); this.chance = chance;
    }

    public double getChance() { return chance; }

    public Type getType() { return type; }
    public String getId() { return id; }
    public ItemStack getTemplate() { return template == null ? null : template.clone(); }
    public int getAmount() { return amount; }

    public ItemStack build(Player player) {
        if (type == Type.ITEM) {
            ItemStack item = template.clone();
            item.setAmount(amount);
            return item;
        }
        return ZaphkielHook.generate(id, amount, player);
    }

    public boolean roll(Random random) { return random.nextDouble() * 100.0 < chance; }
}
