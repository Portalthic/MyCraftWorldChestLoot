package com.mycraft.worldchestloot;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

interface LootNode {
    double probability();

    void generate(Player player, Random random, List<ItemStack> result);

    void collectRewards(List<Reward> rewards);
}

final class ItemLootNode implements LootNode {
    private final Reward reward;
    private final int bonusAmount;

    ItemLootNode(Reward reward, int bonusAmount) {
        this.reward = reward;
        this.bonusAmount = Math.max(0, bonusAmount);
    }

    @Override
    public double probability() {
        return reward.getChance();
    }

    @Override
    public void generate(Player player, Random random, List<ItemStack> result) {
        ItemStack item = reward.build(player);
        if (item == null || item.getType() == Material.AIR) return;
        int amount = reward.getAmount() + (bonusAmount == 0 ? 0 : random.nextInt(bonusAmount + 1));
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        result.add(item);
    }

    @Override
    public void collectRewards(List<Reward> rewards) {
        rewards.add(reward);
    }
}

final class CollectionLootNode implements LootNode {
    private final double probability;
    private final int lower;
    private final int upper;
    private final List<LootNode> children;

    CollectionLootNode(double probability, int lower, int upper, List<LootNode> children) {
        this.probability = probability;
        this.lower = lower;
        this.upper = upper;
        this.children = new ArrayList<>(children);
    }

    @Override
    public double probability() {
        return probability;
    }

    @Override
    public void generate(Player player, Random random, List<ItemStack> result) {
        if (upper <= 0) {
            for (LootNode child : children) {
                if (random.nextDouble() * 100.0 < child.probability()) child.generate(player, random, result);
            }
            return;
        }

        int minimum = Math.max(0, Math.min(lower, upper));
        int maximum = Math.max(minimum, Math.max(lower, upper));
        int count = minimum == maximum ? minimum : minimum + random.nextInt(maximum - minimum + 1);
        List<LootNode> candidates = new ArrayList<>(children);
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            double total = 0;
            for (LootNode child : candidates) total += Math.max(0, child.probability());
            if (total <= 0) break;
            double roll = random.nextDouble() * total;
            LootNode selected = candidates.get(candidates.size() - 1);
            for (LootNode child : candidates) {
                roll -= Math.max(0, child.probability());
                if (roll <= 0) {
                    selected = child;
                    break;
                }
            }
            selected.generate(player, random, result);
            candidates.remove(selected);
        }
    }

    @Override
    public void collectRewards(List<Reward> rewards) {
        for (LootNode child : children) child.collectRewards(rewards);
    }
}
