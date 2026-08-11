package com.mycraft.worldchestloot;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

interface LootNode {
    double probability(LootEvaluationContext context);

    default boolean isEligible(LootEvaluationContext context) { return true; }

    void generate(Player player, LootEvaluationContext context, Random random, List<ItemStack> result,
                  boolean allowCollectionDuplicates);

    void collectRewards(List<Reward> rewards);
}

final class ItemLootNode implements LootNode {
    private final Reward reward;
    private final int bonusAmount;
    private final LootProbability probability;

    ItemLootNode(Reward reward, int bonusAmount, LootProbability probability) {
        this.reward = reward;
        this.bonusAmount = Math.max(0, bonusAmount);
        this.probability = probability;
    }

    @Override
    public double probability(LootEvaluationContext context) {
        return probability.resolve(context);
    }

    @Override
    public void generate(Player player, LootEvaluationContext context, Random random, List<ItemStack> result,
                         boolean allowCollectionDuplicates) {
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
    private final LootProbability probability;
    private final int lower;
    private final int upper;
    private final List<LootNode> children;
    private final List<String> conditions;

    CollectionLootNode(LootProbability probability, int lower, int upper, List<LootNode> children,
                       List<String> conditions) {
        this.probability = probability;
        this.lower = lower;
        this.upper = upper;
        this.children = new ArrayList<>(children);
        this.conditions = new ArrayList<>(conditions);
    }

    @Override
    public double probability(LootEvaluationContext context) {
        return probability.resolve(context);
    }

    @Override
    public boolean isEligible(LootEvaluationContext context) {
        return context.collectionEligible(this, conditions);
    }

    @Override
    public void generate(Player player, LootEvaluationContext context, Random random, List<ItemStack> result,
                         boolean allowCollectionDuplicates) {
        if (upper <= 0) {
            for (LootNode child : children) {
                if (child.isEligible(context) && random.nextDouble() * 100.0 < child.probability(context)) {
                    child.generate(player, context, random, result, allowCollectionDuplicates);
                }
            }
            return;
        }

        int minimum = Math.max(0, Math.min(lower, upper));
        int maximum = Math.max(minimum, Math.max(lower, upper));
        int count = minimum == maximum ? minimum : minimum + random.nextInt(maximum - minimum + 1);
        List<LootNode> candidates = new ArrayList<>();
        for (LootNode child : children) if (child.isEligible(context)) candidates.add(child);
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            double total = 0;
            for (LootNode child : candidates) total += Math.max(0, child.probability(context));
            if (total <= 0) break;
            double roll = random.nextDouble() * total;
            LootNode selected = candidates.get(candidates.size() - 1);
            for (LootNode child : candidates) {
                roll -= Math.max(0, child.probability(context));
                if (roll <= 0) {
                    selected = child;
                    break;
                }
            }
            selected.generate(player, context, random, result, allowCollectionDuplicates);
            if (!allowCollectionDuplicates) candidates.remove(selected);
        }
    }

    @Override
    public void collectRewards(List<Reward> rewards) {
        for (LootNode child : children) child.collectRewards(rewards);
    }
}
