package com.mycraft.worldchestloot;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LootEvaluationContextTest {
    @Test
    public void evaluatesNativePlayerAndWorldValues() {
        LootEvaluationContext context = context(6000, false, false, 100, 0.5F,
                20.0, 12, 8.0F, true, null);
        assertCondition(context, "weather == sunny", true);
        assertCondition(context, "time >= 1800", true);
        assertCondition(context, "time == day", true);
        assertCondition(context, "time == night", false);
        assertCondition(context, "permission == mycraft.vip", true);
        assertCondition(context, "permission != mycraft.vip", false);
        assertCondition(context, "permission >= mycraft.vip", false);
        assertCondition(context, "weather > sunny", false);
        assertCondition(context, "level => 100", true);
        assertCondition(context, "experience >= 10000", true);
        assertCondition(context, "health >= 20", true);
        assertCondition(context, "foodlevel >= 10", true);
        assertCondition(context, "saturation >= 8", true);
    }

    @Test
    public void followsPhatLootsDayAndNightBoundaries() {
        assertCondition(context(13000, false, false, 0, 0, 20, 20, 5, false, null),
                "time == day && time == night", true);
        assertCondition(context(23850, false, false, 0, 0, 20, 20, 5, false, null),
                "time == day && time == night", true);
        assertCondition(context(14000, false, false, 0, 0, 20, 20, 5, false, null),
                "time == day", false);
    }

    @Test
    public void resolvesAndCachesDynamicProbability() {
        AtomicInteger calls = new AtomicInteger();
        LootEvaluationContext context = context(0, false, false, 0, 0, 20, 20, 5, false,
                (player, value) -> { calls.incrementAndGet(); return "37.5"; });
        LootProbability first = LootProbability.parse("%dynamic_probability%", 100);
        LootProbability second = LootProbability.parse("%dynamic_probability%", 100);
        assertEquals(37.5, first.resolve(context), 0.0001);
        assertEquals(37.5, first.resolve(context), 0.0001);
        assertEquals(37.5, second.resolve(context), 0.0001);
        assertEquals(1, calls.get());
    }

    @Test
    public void invalidDynamicProbabilitiesBecomeZero() {
        LootEvaluationContext invalid = context(0, false, false, 0, 0, 20, 20, 5, false,
                (player, value) -> "not-a-number");
        assertEquals(0, LootProbability.parse("%invalid%", 100).resolve(invalid), 0.0);
        assertEquals(0, LootProbability.parse("-5", 100).resolve(invalid), 0.0);
    }

    @Test
    public void excludesIneligibleCollectionsBeforeWeightedSelection() {
        LootEvaluationContext context = context(0, false, false, 10, 0, 20, 20, 5, false, null);
        CountingNode eligible = new CountingNode();
        CollectionLootNode restricted = new CollectionLootNode(LootProbability.parse(100, 100),
                1, 1, Collections.singletonList(new CountingNode()),
                Collections.singletonList("level >= 100"));
        CollectionLootNode parent = new CollectionLootNode(LootProbability.parse(100, 100),
                1, 1, Arrays.asList(restricted, eligible), Collections.emptyList());
        parent.generate(context.player(), context, new Random(1), new ArrayList<>(), false);
        assertEquals(1, eligible.generated);
    }

    @Test
    public void collectionMessagesAreNotSupported() {
        LootEvaluationContext context = context(0, false, false, 100, 0, 20, 20, 5, false, null);
        CollectionLootNode collection = new CollectionLootNode(LootProbability.parse(100, 100),
                1, 1, Collections.emptyList(),
                Collections.singletonList("level >= 100 --message hidden"));
        assertFalse(collection.isEligible(context));
    }

    private void assertCondition(LootEvaluationContext context, String condition, boolean expected) {
        assertEquals(expected, ConditionEvaluator.evaluate(Collections.singletonList(condition), context).isPassed());
    }

    private LootEvaluationContext context(long time, boolean storm, boolean thunder, int level, float exp,
                                          double health, int food, float saturation, boolean permission,
                                          java.util.function.BiFunction<Player, String, String> placeholders) {
        World world = (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getTime")) return time;
                    if (method.getName().equals("hasStorm")) return storm;
                    if (method.getName().equals("isThundering")) return thunder;
                    return defaultValue(method.getReturnType());
                });
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getWorld": return world;
                        case "getLevel": return level;
                        case "getExp": return exp;
                        case "getHealth": return health;
                        case "getFoodLevel": return food;
                        case "getSaturation": return saturation;
                        case "hasPermission": return permission;
                        default: return defaultValue(method.getReturnType());
                    }
                });
        return new LootEvaluationContext(player, placeholders);
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class CountingNode implements LootNode {
        int generated;

        @Override public double probability(LootEvaluationContext context) { return 100; }
        @Override public void generate(Player player, LootEvaluationContext context, Random random,
                                       List<ItemStack> result, boolean allowCollectionDuplicates) { generated++; }
        @Override public void collectRewards(List<Reward> rewards) { }
    }
}
