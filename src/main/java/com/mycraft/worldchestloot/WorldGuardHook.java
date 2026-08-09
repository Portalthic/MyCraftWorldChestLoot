package com.mycraft.worldchestloot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WorldGuardHook {
    private final JavaPlugin plugin;
    private Object worldGuard;
    private Method getRegionManager;
    private Method getApplicableRegions;
    private Method getRegions;
    private Method getId;
    private Method getPriority;
    private Class<?> vectorClass;

    WorldGuardHook(JavaPlugin plugin) { this.plugin = plugin; initialize(); }

    private void initialize() {
        try {
            Plugin p = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            if (p == null) return;
            worldGuard = p;
            getRegionManager = p.getClass().getMethod("getRegionManager", World.class);
            Class<?> regionManager = getRegionManager.getReturnType();
            vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            getApplicableRegions = regionManager.getMethod("getApplicableRegions", vectorClass);
            Class<?> set = getApplicableRegions.getReturnType();
            getRegions = set.getMethod("getRegions");
            getId = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion").getMethod("getId");
            getPriority = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion").getMethod("getPriority");
        } catch (Exception ignored) { worldGuard = null; }
    }

    List<String> regions(Location location) {
        if (worldGuard == null) return Collections.emptyList();
        try {
            Object manager = getRegionManager.invoke(worldGuard, location.getWorld());
            Object vector = vectorClass.getConstructor(double.class, double.class, double.class)
                    .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Object set = getApplicableRegions.invoke(manager, vector);
            List<Object> regions = new ArrayList<>((java.util.Set<?>) getRegions.invoke(set));
            Collections.sort(regions, (a, b) -> {
                try { return Integer.compare((Integer) getPriority.invoke(b), (Integer) getPriority.invoke(a)); }
                catch (Exception ignored) { return 0; }
            });
            List<String> result = new ArrayList<>();
            for (Object region : regions) result.add((String) getId.invoke(region));
            return result;
        } catch (Exception ex) { return Collections.emptyList(); }
    }
}
