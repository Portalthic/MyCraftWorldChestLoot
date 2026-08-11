package com.mycraft.worldchestloot;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class PlaceholderHook {
    private final Method setPlaceholders;

    PlaceholderHook(MyCraftWorldChestLoot plugin) {
        Method method = null;
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (dependency != null && dependency.isEnabled()) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true,
                        dependency.getClass().getClassLoader());
                for (Method candidate : api.getMethods()) {
                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (candidate.getName().equals("setPlaceholders")
                            && Modifier.isStatic(candidate.getModifiers())
                            && parameters.length == 2
                            && parameters[0].isAssignableFrom(Player.class)
                            && parameters[1] == String.class
                            && candidate.getReturnType() == String.class) {
                        method = candidate;
                        break;
                    }
                }
                if (method == null) throw new NoSuchMethodException("setPlaceholders player overload");
            } catch (ReflectiveOperationException | LinkageError ex) {
                plugin.getLogger().warning("PlaceholderAPI hook could not be initialized: " + ex.getMessage());
            }
        }
        setPlaceholders = method;
    }

    String resolve(Player player, String placeholder) {
        if (setPlaceholders == null) return null;
        try {
            Object result = setPlaceholders.invoke(null, player, placeholder);
            return result == null ? null : String.valueOf(result);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
