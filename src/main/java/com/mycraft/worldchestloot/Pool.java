package com.mycraft.worldchestloot;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Pool {
    final String name;
    final String displayName;
    final ResetSpec reset;
    final int cooldownSeconds;
    final int rolls;
    final boolean globalReset;
    final boolean roundDownTime;
    final List<Reward> rewards;
    final List<LootNode> legacyLoot;
    final List<String> lootConditions;

    Pool(String name, int cooldownSeconds, int rolls, boolean globalReset, List<Reward> rewards) {
        this(name, name, ResetSpec.duration(cooldownSeconds), rolls, globalReset, false,
                rewards, null, null);
    }

    Pool(String name, String displayName, ResetSpec reset, int rolls, boolean globalReset,
         boolean roundDownTime, List<Reward> rewards, List<LootNode> legacyLoot,
         List<String> lootConditions) {
        this.name = name; this.displayName = displayName == null || displayName.isEmpty() ? name : displayName;
        this.reset = reset == null ? ResetSpec.duration(0) : reset;
        this.cooldownSeconds = this.reset.editorSeconds();
        this.rolls = Math.max(1, rolls); this.globalReset = globalReset;
        this.roundDownTime = roundDownTime; this.rewards = rewards;
        this.legacyLoot = legacyLoot == null ? null : new ArrayList<>(legacyLoot);
        this.lootConditions = lootConditions == null ? new ArrayList<>() : new ArrayList<>(lootConditions);
    }

    List<ItemStack> roll(Player player, LootEvaluationContext context, Random random,
                         boolean allowCollectionDuplicates) {
        List<ItemStack> result = new ArrayList<>();
        if (legacyLoot != null) {
            for (LootNode node : legacyLoot) {
                if (node.isEligible(context) && random.nextDouble() * 100.0 < node.probability(context)) {
                    node.generate(player, context, random, result, allowCollectionDuplicates);
                }
            }
            return result;
        }
        List<Reward> candidates = new ArrayList<>(rewards);
        for (int i = 0; i < rolls && !candidates.isEmpty(); i++) {
            double total = 0;
            for (Reward reward : candidates) total += Math.max(0, reward.getChance());
            if (total <= 0) break;
            double selected = random.nextDouble() * total;
            Reward chosen = candidates.get(candidates.size() - 1);
            for (Reward reward : candidates) {
                selected -= Math.max(0, reward.getChance());
                if (selected <= 0) { chosen = reward; break; }
            }
            ItemStack item = chosen.build(player);
            if (item != null && item.getType() != org.bukkit.Material.AIR) result.add(item);
            candidates.remove(chosen);
        }
        return result;
    }

    List<Reward> getRewards() { return new ArrayList<>(rewards); }
    int getCooldownSeconds() { return cooldownSeconds; }
    boolean isGlobalReset() { return globalReset; }
    boolean isRoundDownTime() { return roundDownTime; }
    ResetSpec getReset() { return reset; }
    String getDisplayName() { return displayName; }
    List<String> getLootConditions() { return new ArrayList<>(lootConditions); }
}
