package com.mycraft.worldchestloot;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

final class ZaphkielHook {
    private static Method generate;
    private static Object apiInstance;
    private static boolean checked;

    private ZaphkielHook() { }

    static ItemStack generate(String id, int amount, Player player) {
        try {
            if (!checked) initialize();
            if (generate == null) return null;
            ItemStack item = (ItemStack) generate.invoke(apiInstance, id, player);
            if (item != null) item.setAmount(amount);
            return item;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void initialize() throws Exception {
        checked = true;
        Class<?> api = Class.forName("ink.ptms.zaphkiel.ZaphkielAPI");
        apiInstance = api.getField("INSTANCE").get(null);
        generate = api.getMethod("getItemStack", String.class, Player.class);
    }

    static String identify(ItemStack item) {
        try {
            if (!checked) initialize();
            Class<?> api = Class.forName("ink.ptms.zaphkiel.ZaphkielAPI");
            Method method = api.getMethod("getName", ItemStack.class);
            return (String) method.invoke(apiInstance, item);
        } catch (Exception ex) {
            return null;
        }
    }
}
