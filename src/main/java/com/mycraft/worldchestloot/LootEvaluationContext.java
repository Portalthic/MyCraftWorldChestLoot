package com.mycraft.worldchestloot;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

final class LootEvaluationContext implements ConditionEvaluator.PlaceholderResolver {
    private final Player player;
    private final BiFunction<Player, String, String> placeholderResolver;
    private final Map<String, String> placeholderCache = new HashMap<>();
    private final Map<LootProbability, Double> probabilityCache = new HashMap<>();
    private final Map<CollectionLootNode, Boolean> collectionConditionCache = new IdentityHashMap<>();

    LootEvaluationContext(Player player, PlaceholderHook placeholders) {
        this(player, placeholders == null ? null : placeholders::resolve);
    }

    LootEvaluationContext(Player player, BiFunction<Player, String, String> placeholderResolver) {
        this.player = player;
        this.placeholderResolver = placeholderResolver;
    }

    Player player() { return player; }

    @Override
    public String resolve(String value) {
        if (value.startsWith("%") && value.endsWith("%")) return resolvePlaceholder(value);
        switch (value.toLowerCase(Locale.ROOT)) {
            case "weather": return weather();
            case "time": return String.valueOf(player.getWorld().getTime());
            case "permission": return "permission";
            case "level": return String.valueOf(player.getLevel());
            case "experience": return String.valueOf(totalExperience(player));
            case "health": return String.valueOf(player.getHealth());
            case "foodlevel": return String.valueOf(player.getFoodLevel());
            case "saturation": return String.valueOf(player.getSaturation());
            default: return null;
        }
    }

    @Override
    public boolean resolvesBareValues() { return true; }

    @Override
    public Boolean compare(String leftSource, String leftValue, String operator,
                           String rightSource, String rightValue) {
        String variable = leftSource.toLowerCase(Locale.ROOT);
        if (variable.equals("permission")) {
            if (!operator.equals("==") && !operator.equals("!=")) return false;
            String permission = rightSource.startsWith("%") ? rightValue : rightSource;
            boolean hasPermission = player.hasPermission(permission);
            return operator.equals("==") ? hasPermission : !hasPermission;
        }
        if (variable.equals("weather")) {
            if (!operator.equals("==") && !operator.equals("!=")) return false;
            boolean matches = leftValue.equalsIgnoreCase(rightValue);
            return operator.equals("==") ? matches : !matches;
        }
        if (variable.equals("time")
                && (rightValue.equalsIgnoreCase("day") || rightValue.equalsIgnoreCase("night"))) {
            if (!operator.equals("==") && !operator.equals("!=")) return false;
            long time = player.getWorld().getTime();
            boolean matches = rightValue.equalsIgnoreCase("day")
                    ? time <= 13000 || time >= 23850
                    : time >= 13000 && time <= 23850;
            return operator.equals("==") ? matches : !matches;
        }
        return null;
    }

    double probability(LootProbability probability, String configured, double fallback) {
        Double cached = probabilityCache.get(probability);
        if (cached != null) return cached;
        if (configured == null || configured.trim().isEmpty()) return fallback;
        String resolved = configured;
        if (configured.indexOf('%') >= 0) {
            resolved = resolvePlaceholder(configured);
            if (resolved == null || resolved.equals(configured)) {
                probabilityCache.put(probability, 0.0);
                return 0;
            }
        }
        try {
            double value = Double.parseDouble(resolved.trim());
            double result = Double.isFinite(value) ? Math.max(0, value) : 0;
            probabilityCache.put(probability, result);
            return result;
        } catch (NumberFormatException ex) {
            probabilityCache.put(probability, 0.0);
            return 0;
        }
    }

    boolean collectionEligible(CollectionLootNode collection, List<String> conditions) {
        Boolean cached = collectionConditionCache.get(collection);
        if (cached != null) return cached;
        boolean result = ConditionEvaluator.evaluateSilently(conditions, this).isPassed();
        collectionConditionCache.put(collection, result);
        return result;
    }

    private String resolvePlaceholder(String placeholder) {
        if (placeholderCache.containsKey(placeholder)) return placeholderCache.get(placeholder);
        String resolved = placeholderResolver == null ? null : placeholderResolver.apply(player, placeholder);
        placeholderCache.put(placeholder, resolved);
        return resolved;
    }

    private String weather() {
        World world = player.getWorld();
        if (world.isThundering()) return "thundering";
        return world.hasStorm() ? "raining" : "sunny";
    }

    static int totalExperience(Player player) {
        int level = player.getLevel();
        long experience = Math.round(experienceToNextLevel(level) * player.getExp());
        for (int current = level - 1; current >= 0; current--) {
            experience += experienceToNextLevel(current);
            if (experience >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) Math.max(0, experience);
    }

    private static int experienceToNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }
}
