package com.mycraft.worldchestloot;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MyCraftWorldChestLoot extends JavaPlugin {
    private final Map<String, Pool> pools = new HashMap<>();
    private final Map<String, File> poolFiles = new HashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, CachedInventory> inventoryCache = new HashMap<>();
    private final Map<UUID, OpenInventory> openInventories = new HashMap<>();
    private final Map<String, Integer> chestViewers = new HashMap<>();
    private final Random random = new Random();
    private Set<Material> chestMaterials = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST);
    private WorldGuardHook worldGuard;
    private File cooldownFile;
    private File lootTablesDirectory;
    private MessageManager messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lootTablesDirectory = new File(getDataFolder(), "LootTables");
        if (!lootTablesDirectory.isDirectory() && !lootTablesDirectory.mkdirs()) {
            getLogger().warning("Could not create LootTables directory.");
        }
        saveBundledResourceIfMissing("LootTables/SampleLoot.yml");
        saveBundledResourceIfMissing("LootTables/SampleLootZaphkiel.yml");
        saveBundledResourceIfMissing("message.yml");
        messages = new MessageManager(this, new File(getDataFolder(), "message.yml"));
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        worldGuard = new WorldGuardHook(this);
        loadData();
        getServer().getPluginManager().registerEvents(new ChestLootListener(this), this);
        getServer().getPluginManager().registerEvents(new PoolEditorListener(this), this);
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::saveCooldowns, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, this::forgetExpiredInventories, 20L, 20L);
        getLogger().info("MyCraftWorldChestLoot " + getDescription().getVersion() + " enabled with " + pools.size() + " pool(s).");
    }

    @Override
    public void onDisable() {
        inventoryCache.clear();
        openInventories.clear();
        chestViewers.clear();
        saveCooldowns();
    }

    boolean isChest(Material material) {
        return chestMaterials.contains(material);
    }

    ResolvedLink resolveLink(Block block) {
        String blockKey = blockLinkKey(block);
        if (blockKey == null) return null;
        org.bukkit.Location location = block.getLocation();
        String worldName = location.getWorld().getName();
        ConfigurationSection linksRoot = getConfig().getConfigurationSection("links");
        ConfigurationSection world = linksRoot == null ? null : linksRoot.getConfigurationSection(worldName);
        if (world != null) {
            for (String region : worldGuard.regions(location)) {
                ConfigurationSection scope = getLinkScope(world, region);
                ResolvedLink link = scope == null ? null : parseLink(materialString(scope, blockKey));
                if (link != null) return link;
            }
            ConfigurationSection defaultScope = getLinkScope(world, null);
            ResolvedLink worldDefault = defaultScope == null ? null : parseLink(materialString(defaultScope, blockKey));
            if (worldDefault != null) return worldDefault;
            // PhatLoots AutoLink compatibility: links.<world>.<material>: <pool>
            ResolvedLink directDefault = parseLink(materialString(world, blockKey));
            if (directDefault != null) return directDefault;
        }
        // The fallback pool retains its original meaning: unlinked configured chest types only.
        if (!isChest(block.getType())) return null;
        String fallback = getConfig().getString("settings.default-pool", "");
        return parseLink(fallback);
    }

    String blockLinkKey(Block block) {
        if (block == null) return null;
        if (block.getType() == Material.SKULL) {
            String owner = skullOwnerName(block);
            String prefix = getConfig().getString("settings.skull-model-prefix", "model:");
            if (owner == null || prefix == null || prefix.isEmpty() || !owner.startsWith(prefix)) return null;
            String model = owner.substring(prefix.length()).trim();
            return model.isEmpty() ? null : "SKULL:" + model;
        }
        return isChest(block.getType()) ? block.getType().name() : null;
    }

    private String skullOwnerName(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Skull)) return null;
        String owner = ((Skull) state).getOwner();
        if (owner != null && !owner.isEmpty()) return owner;

        // Some 1.12 furniture plugins populate CraftSkull's GameProfile directly.
        for (Class<?> type = state.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getName().equals("com.mojang.authlib.GameProfile")) continue;
                try {
                    field.setAccessible(true);
                    Object profile = field.get(state);
                    if (profile == null) continue;
                    Method getName = profile.getClass().getMethod("getName");
                    Object name = getName.invoke(profile);
                    if (name != null && !String.valueOf(name).isEmpty()) return String.valueOf(name);
                } catch (ReflectiveOperationException | SecurityException ignored) { }
            }
        }
        return null;
    }

    private ResolvedLink parseLink(String configured) {
        if (configured == null) return null;
        int separator = configured.indexOf(':');
        String poolName = (separator < 0 ? configured : configured.substring(0, separator)).trim();
        if (!pools.containsKey(poolName)) return null;
        String title = separator < 0 ? null : configured.substring(separator + 1).trim();
        return new ResolvedLink(poolName, title == null || title.isEmpty() ? null : title);
    }

    long cooldownRemaining(String poolName, Block block, Player player) {
        block = canonicalChest(block);
        Pool pool = pools.get(poolName);
        if (pool == null || pool.cooldownSeconds <= 0) return 0;
        Long until = cooldowns.get(cooldownKey(poolName, block, player));
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    void openLoot(Player player, Block block, ResolvedLink link) {
        block = canonicalChest(block);
        String poolName = link.poolName;
        Pool pool = pools.get(poolName);
        if (pool == null) return;
        long remaining = cooldownRemaining(poolName, block, player);
        String cacheKey = inventoryCacheKey(pool, poolName, block, player);
        CachedInventory cached = inventoryCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt <= now) {
            inventoryCache.remove(cacheKey);
            cached = null;
        }

        if (remaining <= 0 && cached != null) {
            inventoryCache.remove(cacheKey);
            cached = null;
        }

        int size = chestInventorySize(block);
        String displayName = link.title == null ? pool.getDisplayName().replace('_', ' ') : link.title;
        String title = color(getConfig().getString("settings.ChestName",
                getConfig().getString("settings.default-gui-title", "&6<name>"))).replace("<name>", displayName);
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory;
        if (cached != null) {
            inventory = cached.inventory;
        } else {
            inventory = getServer().createInventory(null, size, title);
            if (remaining <= 0) {
                boolean allowDuplicates = getConfig().getBoolean(
                        "settings.AllowDuplicateItemsFromCollections", true);
                addLoot(inventory, pool.roll(player, random, allowDuplicates), player);
                long cooldownStart = pool.roundDownTime ? roundDownCooldownStart(now, pool.cooldownSeconds) : now;
                long until = pool.cooldownSeconds < 0 ? Long.MAX_VALUE
                        : cooldownStart + pool.cooldownSeconds * 1000L;
                cooldowns.put(cooldownKey(poolName, block, player), until);
            }
            cached = new CachedInventory(inventory, 0);
            inventoryCache.put(cacheKey, cached);
        }
        if (remaining > 0) send(player, "cooldown-remaining", "<time>", formatDuration(remaining));
        cached.expiresAt = now + Math.max(0, getConfig().getLong("settings.ForgetInventoryTime", 60)) * 1000L;
        if (openVirtualChest(player, block, poolName, inventory)) {
            runLootCommands(player, block, poolName, true);
        }
    }

    private void addLoot(Inventory inventory, List<ItemStack> items, Player player) {
        boolean overflow = false;
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) emptySlots.add(slot);
        }
        if (getConfig().getBoolean("settings.ShuffleLoot", false)) Collections.shuffle(emptySlots, random);

        int nextSlot = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (nextSlot >= emptySlots.size()) {
                overflow = true;
                continue;
            }
            inventory.setItem(emptySlots.get(nextSlot++), item.clone());
        }
        if (overflow) send(player, "inventory-overflow");
    }

    private long roundDownCooldownStart(long now, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return now;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        if (cooldownSeconds % 60 == 0) {
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (cooldownSeconds % 3600 == 0) {
                calendar.set(Calendar.MINUTE, 0);
                if (cooldownSeconds % 86400 == 0) calendar.set(Calendar.HOUR_OF_DAY, 0);
            }
        }
        return calendar.getTimeInMillis();
    }

    private boolean openVirtualChest(Player player, Block block, String poolName, Inventory inventory) {
        String chestKey = chestLocationKey(block);
        if (player.openInventory(inventory) == null) return false;
        boolean animateChest = block.getState() instanceof Chest;
        openInventories.put(player.getUniqueId(), new OpenInventory(block, chestKey, poolName, animateChest));
        player.playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.75F, 0.95F);
        if (animateChest) {
            int viewers = chestViewers.containsKey(chestKey) ? chestViewers.get(chestKey) : 0;
            chestViewers.put(chestKey, viewers + 1);
            if (viewers == 0) playChestAnimation(block, true);
        }
        return true;
    }

    void closeVirtualChest(Player player) {
        OpenInventory opened = openInventories.remove(player.getUniqueId());
        if (opened == null) return;
        player.playSound(opened.block.getLocation(), Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.75F, 0.95F);
        if (opened.animateChest) {
            int viewers = chestViewers.containsKey(opened.chestKey) ? chestViewers.get(opened.chestKey) - 1 : 0;
            if (viewers <= 0) {
                chestViewers.remove(opened.chestKey);
                Block closingBlock = opened.block;
                String closingKey = opened.chestKey;
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (!chestViewers.containsKey(closingKey)) playChestAnimation(closingBlock, false);
                }, 1L);
            } else {
                chestViewers.put(opened.chestKey, viewers);
            }
        }
        runLootCommands(player, opened.block, opened.poolName, false);
    }

    private void runLootCommands(Player player, Block block, String poolName, boolean opening) {
        String phase = opening ? "open" : "close";
        String consoleKey = phase + "-console-command";
        String playerKey = phase + "-player-command";
        String defaultConsoleKey = "settings.default-" + consoleKey;
        String defaultPlayerKey = "settings.default-" + playerKey;
        for (String command : commandList(poolName, consoleKey, defaultConsoleKey)) {
            dispatchConfiguredCommand(command, player, block, poolName, false);
        }
        for (String command : commandList(poolName, playerKey, defaultPlayerKey)) {
            dispatchConfiguredCommand(command, player, block, poolName, true);
        }
    }

    private List<String> commandList(String poolName, String poolKey, String defaultPath) {
        ConfigurationSection settings = lootTableSettings(poolName);
        Object value = settings != null && settings.contains(poolKey)
                ? settings.get(poolKey) : getConfig().get(defaultPath);
        List<String> commands = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) if (item != null) commands.add(String.valueOf(item));
        } else if (value != null) {
            commands.add(String.valueOf(value));
        }
        return commands;
    }

    private void dispatchConfiguredCommand(String configured, Player player, Block block, String poolName,
                                           boolean asPlayer) {
        String command = configured == null ? "" : configured.trim();
        if (command.isEmpty()) return;
        if (command.startsWith("/")) command = command.substring(1).trim();
        command = command.replace("<player>", player.getName())
                .replace("<uuid>", player.getUniqueId().toString())
                .replace("<pool>", poolName)
                .replace("<world>", block.getWorld().getName())
                .replace("<x>", String.valueOf(block.getX()))
                .replace("<y>", String.valueOf(block.getY()))
                .replace("<z>", String.valueOf(block.getZ()));
        if (command.isEmpty()) return;
        try {
            if (asPlayer) player.performCommand(command);
            else getServer().dispatchCommand(getServer().getConsoleSender(), command);
        } catch (RuntimeException ex) {
            getLogger().warning("Could not dispatch " + (asPlayer ? "player" : "console")
                    + " loot command for " + poolName + ": " + ex.getMessage());
        }
    }

    Block canonicalChest(Block block) {
        if (block == null || !(block.getState() instanceof Chest)) return block;
        Inventory inventory = ((Chest) block.getState()).getInventory();
        if (!(inventory instanceof DoubleChestInventory)) return block;
        InventoryHolder holder = ((DoubleChestInventory) inventory).getLeftSide().getHolder();
        return holder instanceof Chest ? ((Chest) holder).getBlock() : block;
    }

    private void playChestAnimation(Block block, boolean open) {
        List<Block> parts = chestParts(block);
        for (Player viewer : block.getWorld().getPlayers()) {
            for (Block part : parts) ChestAnimation.play(this, viewer, part, open);
        }
    }

    private List<Block> chestParts(Block block) {
        List<Block> parts = new ArrayList<>();
        parts.add(block);
        if (!(block.getState() instanceof Chest)) return parts;
        Inventory inventory = ((Chest) block.getState()).getInventory();
        if (!(inventory instanceof DoubleChestInventory)) return parts;
        InventoryHolder right = ((DoubleChestInventory) inventory).getRightSide().getHolder();
        if (right instanceof Chest) {
            Block rightBlock = ((Chest) right).getBlock();
            if (!rightBlock.equals(block)) parts.add(rightBlock);
        }
        return parts;
    }

    private int chestInventorySize(Block block) {
        if (block.getState() instanceof Chest) return ((Chest) block.getState()).getInventory().getSize();
        int configured = getConfig().getInt("settings.default-gui-size", 27);
        return Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
    }

    private String inventoryCacheKey(Pool pool, String poolName, Block block, Player player) {
        String owner = pool.globalReset ? "global" : player.getUniqueId().toString();
        return owner + "@" + chestLocationKey(block) + ":" + poolName;
    }

    private String chestLocationKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void forgetExpiredInventories() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CachedInventory> entry : new ArrayList<>(inventoryCache.entrySet())) {
            if (entry.getValue().expiresAt <= now) inventoryCache.remove(entry.getKey());
        }
    }

    private String cooldownKey(String poolName, Block block, Player player) {
        block = canonicalChest(block);
        String location = chestLocationKey(block);
        Pool pool = pools.get(poolName);
        return (pool != null && pool.globalReset ? "global:" : "player:" + player.getUniqueId() + ":") + location + ":" + poolName;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    void send(CommandSender sender, String key, String... replacements) {
        messages.send(sender, key, replacements);
    }

    String message(String key, String... replacements) {
        return messages.text(key, replacements);
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        long remainingSeconds = seconds % 60;
        StringBuilder value = new StringBuilder();
        if (days > 0) value.append(days).append("d ");
        if (hours > 0) value.append(hours).append("h ");
        if (minutes > 0) value.append(minutes).append("m ");
        if (remainingSeconds > 0 || value.length() == 0) value.append(remainingSeconds).append("s");
        return value.toString().trim();
    }

    private void loadData() {
        chestMaterials.clear();
        for (String value : getConfig().getStringList("settings.chest-materials")) {
            try { chestMaterials.add(Material.valueOf(value.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ex) { getLogger().warning("Unknown chest material: " + value); }
        }
        pools.clear();
        poolFiles.clear();
        loadLootTables();
        loadCooldowns();
    }

    private void loadLootTables() {
        File[] files = lootTablesDirectory.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".yml") || lower.endsWith(".yaml");
        });
        if (files == null) return;
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            if (loadPhatLootFile(file)) continue;
            getLogger().warning("Skipped invalid loot table: " + file.getName());
        }
    }

    private boolean loadPhatLootFile(File file) {
        String raw;
        try {
            raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            getLogger().warning("Could not read loot table " + file.getName() + ": " + ex.getMessage());
            return true;
        }
        // The upstream sample was saved by a newer Bukkit version; map renamed materials back to 1.12 names.
        raw = raw.replace("type: WOODEN_", "type: WOOD_")
                .replace("type: GOLDEN_", "type: GOLD_");
        raw = raw.replaceAll("(?m)==: PhatLoot\\s*$", "mcwcl-legacy-type: PhatLoot")
                .replaceAll("(?m)==: LootCollection\\s*$", "mcwcl-legacy-type: LootCollection")
                .replaceAll("(?m)==: ZaphkielItem\\s*$", "mcwcl-legacy-type: ZaphkielItem")
                .replaceAll("(?m)==: Item\\s*$", "mcwcl-legacy-type: Item")
                .replaceAll("(?m)==: Money\\s*$", "mcwcl-legacy-type: Money")
                .replaceAll("(?m)==: Experience\\s*$", "mcwcl-legacy-type: Experience");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException ex) {
            getLogger().warning("Could not parse PhatLoots table " + file.getName() + ": " + ex.getMessage());
            return true;
        }

        String fileName = file.getName().replaceFirst("\\.(?i:yml|yaml)$", "");
        String rootName = yaml.isConfigurationSection(fileName) ? fileName
                : yaml.getKeys(false).isEmpty() ? null : yaml.getKeys(false).iterator().next();
        ConfigurationSection root = rootName == null ? null : yaml.getConfigurationSection(rootName);
        if (root == null || !root.isList("LootList")) {
            getLogger().warning("Skipped invalid loot table: " + file.getName());
            return true;
        }

        List<LootNode> nodes = new ArrayList<>();
        int[] unsupported = new int[1];
        for (Map<?, ?> entry : root.getMapList("LootList")) {
            LootNode node = parseLegacyNode(entry, fileName, unsupported);
            if (node != null) nodes.add(node);
        }
        List<Reward> rewards = new ArrayList<>();
        for (LootNode node : nodes) node.collectRewards(rewards);
        ConfigurationSection tableSettings = lootTableSettings(fileName);
        ConfigurationSection reset = tableSettings == null ? null : tableSettings.getConfigurationSection("Reset");
        if (reset == null) reset = root.getConfigurationSection("Reset");
        int cooldown = reset == null ? getConfig().getInt("settings.default-cooldown-seconds", 3600)
                : reset.getInt("Days") * 86400 + reset.getInt("Hours") * 3600
                + reset.getInt("Minutes") * 60 + reset.getInt("Seconds");
        boolean global = tableSettings != null && tableSettings.contains("Global")
                ? tableSettings.getBoolean("Global")
                : root.getBoolean("Global", getConfig().getBoolean("settings.default-global-reset", false));
        String displayName = tableSettings != null && tableSettings.contains("Name")
                ? tableSettings.getString("Name") : root.getString("Name", fileName);
        boolean roundDown = tableSettings != null && tableSettings.contains("RoundDownTime")
                ? tableSettings.getBoolean("RoundDownTime")
                : root.getBoolean("RoundDownTime", false);
        pools.put(fileName, new Pool(fileName, displayName, cooldown, 1, global, roundDown, rewards, nodes));
        poolFiles.put(fileName, file);
        if (unsupported[0] > 0) {
            getLogger().info("Ignored " + unsupported[0] + " non-item reward(s) in " + file.getName()
                    + "; this plugin only handles item rewards.");
        }
        return true;
    }

    private void saveBundledResourceIfMissing(String resourcePath) {
        File target = new File(getDataFolder(), resourcePath);
        if (target.exists()) return;
        if (getResource(resourcePath) != null) saveResource(resourcePath, false);
    }

    @SuppressWarnings("unchecked")
    private LootNode parseLegacyNode(Map<?, ?> entry, String poolName, int[] unsupported) {
        String type = String.valueOf(entry.get("mcwcl-legacy-type"));
        double probability = decimal(entry.get("Probability"), 100);
        if (type.equals("LootCollection")) {
            List<LootNode> children = new ArrayList<>();
            Object list = entry.get("LootList");
            if (list instanceof List) {
                for (Object child : (List<?>) list) {
                    if (!(child instanceof Map)) continue;
                    LootNode node = parseLegacyNode((Map<?, ?>) child, poolName, unsupported);
                    if (node != null) children.add(node);
                }
            }
            return new CollectionLootNode(probability,
                    integer(entry.get("LowerNumberOfLoots"), 1),
                    integer(entry.get("UpperNumberOfLoots"), 1), children);
        }
        if (type.equals("ZaphkielItem")) {
            String id = String.valueOf(entry.containsKey("ItemID") ? entry.get("ItemID") : entry.get("id"));
            int amount = integer(entry.get("Amount"), 1);
            return new ItemLootNode(new Reward(Reward.Type.ZAPHKIEL, null, id, amount, probability),
                    integer(entry.get("BonusAmount"), 0));
        }
        if (type.equals("Item")) {
            Object serialized = entry.get("ItemStack");
            ItemStack item = serialized instanceof ItemStack ? ((ItemStack) serialized).clone() : null;
            if (item == null && serialized instanceof Map) {
                try { item = ItemStack.deserialize((Map<String, Object>) serialized); }
                catch (Exception ignored) { }
            }
            if (item != null) {
                return new ItemLootNode(new Reward(Reward.Type.ITEM, item, null, item.getAmount(), probability),
                        integer(entry.get("BonusAmount"), 0));
            }
            getLogger().warning("Skipped invalid ItemStack in pool " + poolName + ".");
        }
        unsupported[0]++;
        return null;
    }

    private void loadCooldowns() {
        cooldowns.clear();
        if (!cooldownFile.exists()) return;
        for (Map.Entry<String, Object> entry : YamlConfiguration.loadConfiguration(cooldownFile).getValues(false).entrySet()) {
            if (entry.getValue() instanceof Number) cooldowns.put(entry.getKey(), ((Number) entry.getValue()).longValue());
        }
    }

    private void saveCooldowns() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : new HashMap<>(cooldowns).entrySet()) {
            if (entry.getValue() > now) yaml.set(entry.getKey(), entry.getValue());
        }
        try { yaml.save(cooldownFile); }
        catch (IOException ex) { getLogger().warning("Could not save cooldowns: " + ex.getMessage()); }
    }

    private File poolFile(String name) {
        File existing = poolFiles.get(name);
        return existing == null ? new File(lootTablesDirectory, name + ".yml") : existing;
    }

    private void savePool(String name, YamlConfiguration yaml) {
        try { yaml.save(poolFile(name)); }
        catch (IOException ex) { getLogger().warning("Could not save pool " + name + ": " + ex.getMessage()); }
    }

    private int integer(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ex) { return fallback; }
    }

    private double decimal(Object value, double fallback) {
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception ex) { return fallback; }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcwcl.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "help");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            messages.reload();
            loadData();
            send(sender, "reload-success");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            send(sender, "pool-list", "<pools>", String.join(", ", pools.keySet()));
            return true;
        }
        if (args[0].equalsIgnoreCase("show")) {
            if (args.length < 3) {
                send(sender, "show-usage");
                return true;
            }
            Player target = findOnlinePlayer(args[2]);
            if (target == null) {
                send(sender, "player-not-found");
                return true;
            }
            if (!openPoolPreview(target, args[1])) {
                send(sender, "pool-not-found");
                return true;
            }
            send(sender, "show-success", "<player>", target.getName(), "<pool>", args[1]);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            if (args.length >= 2) resetNamed(sender, args[1]);
            else if (sender instanceof Player) resetTarget((Player) sender);
            else send(sender, "console-reset-required");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("clean")) {
            int removed = cleanCooldowns();
            send(sender, "clean-success", "<count>", String.valueOf(removed));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("make")) {
            if (!(sender instanceof Player)) { send(sender, "player-only"); return true; }
            Player player = (Player) sender;
            createPool(player, args[1]);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player)) { send(sender, "player-only"); return true; }
            Player player = (Player) sender;
            openPoolEditor(player, args[1]);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("link")) {
            if (!(sender instanceof Player)) { send(sender, "player-only"); return true; }
            Player player = (Player) sender;
            linkRegionBlock(player, args[1]);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("unlink")) {
            if (!(sender instanceof Player)) { send(sender, "player-only"); return true; }
            Player player = (Player) sender;
            unlinkRegionBlock(player);
            return true;
        }
        send(sender, "help");
        return true;
    }

    private Player findOnlinePlayer(String input) {
        try {
            Player byUuid = getServer().getPlayer(UUID.fromString(input));
            if (byUuid != null) return byUuid;
        } catch (IllegalArgumentException ignored) { }
        Player exact = getServer().getPlayerExact(input);
        return exact != null ? exact : getServer().getPlayer(input);
    }

    private void resetNamed(CommandSender sender, String name) {
        if (name.equals("*")) {
            int count = cooldowns.size();
            cooldowns.clear();
            saveCooldowns();
            send(sender, "reset-all-success", "<count>", String.valueOf(count));
            return;
        }
        if (!pools.containsKey(name)) {
            send(sender, "pool-not-found");
            return;
        }
        int count = 0;
        for (String key : new ArrayList<>(cooldowns.keySet())) {
            if (key.endsWith(":" + name)) { cooldowns.remove(key); count++; }
        }
        saveCooldowns();
        send(sender, "reset-pool-success", "<count>", String.valueOf(count), "<pool>", name);
    }

    private void resetTarget(Player player) {
        Block block = player.getTargetBlock(null, 10);
        if (block == null || blockLinkKey(block) == null) {
            send(player, "target-chest-required");
            return;
        }
        block = canonicalChest(block);
        String location = ":" + block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ() + ":";
        int count = 0;
        for (String key : new ArrayList<>(cooldowns.keySet())) {
            if (key.contains(location)) { cooldowns.remove(key); count++; }
        }
        saveCooldowns();
        send(player, "reset-target-success", "<count>", String.valueOf(count));
    }

    private int cleanCooldowns() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Map.Entry<String, Long> entry : new ArrayList<>(cooldowns.entrySet())) {
            if (entry.getValue() <= now) { cooldowns.remove(entry.getKey()); count++; }
        }
        if (count > 0) saveCooldowns();
        return count;
    }

    private void openPoolEditor(Player player, String name) {
        Pool pool = pools.get(name);
        if (pool == null) {
            send(player, "pool-not-found");
            return;
        }
        PoolEditorHolder holder = new PoolEditorHolder(name, pool.getCooldownSeconds(), pool.isGlobalReset());
        String editorTitle = message("editor-title", "<pool>", name);
        if (editorTitle.length() > 32) editorTitle = editorTitle.substring(0, 32);
        Inventory inventory = getServer().createInventory(holder, 54, editorTitle);
        holder.setInventory(inventory);
        for (Reward reward : pool.getRewards()) {
            ItemStack item = reward.build(player);
            if (item != null && item.getType() != Material.AIR && holder.getEntries().size() < 45) {
                holder.getEntries().add(new PoolEditorEntry(item, reward.getChance()));
            }
        }
        PoolEditorListener.refresh(this, holder);
        player.openInventory(inventory);
        send(player, "editor-opened");
    }

    private boolean openPoolPreview(Player player, String name) {
        Pool pool = pools.get(name);
        if (pool == null) return false;
        PoolPreviewHolder holder = new PoolPreviewHolder();
        String title = message("preview-title", "<pool>", name);
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = getServer().createInventory(holder, 54, title);
        holder.setInventory(inventory);
        for (Reward reward : pool.getRewards()) {
            ItemStack item = reward.build(player);
            if (item != null && item.getType() != Material.AIR && holder.getEntries().size() < 45) {
                holder.getEntries().add(new PoolEditorEntry(item, reward.getChance()));
            }
        }
        PoolEditorListener.refreshPreview(this, holder);
        player.openInventory(inventory);
        return true;
    }

    void saveEditedPool(String name, List<PoolEditorEntry> entries, int cooldownSeconds, boolean globalReset) {
        if (!pools.containsKey(name)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (PoolEditorEntry entry : entries) {
            ItemStack item = entry.getItem();
            Map<String, Object> reward = new LinkedHashMap<>();
            String zaphkielId = ZaphkielHook.identify(item);
            if (zaphkielId != null && !zaphkielId.isEmpty()) {
                reward.put("==", "ZaphkielItem");
                reward.put("ItemID", zaphkielId);
                reward.put("Amount", entry.getAmount());
            } else {
                reward.put("==", "Item");
                ItemStack saved = item.clone();
                saved.setAmount(entry.getAmount());
                reward.put("ItemStack", saved);
            }
            reward.put("Probability", entry.getChance());
            rewards.add(reward);
        }
        root.put("LootList", rewards);
        yaml.set(name, root);
        Pool current = pools.get(name);
        savePoolSettings(name, cooldownSeconds, globalReset, current != null && current.isRoundDownTime());
        savePool(name, yaml);
        loadData();
    }

    private void createPool(Player player, String name) {
        if (pools.containsKey(name)) {
            send(player, "pool-exists");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        int cooldown = getConfig().getInt("settings.default-cooldown-seconds", 3600);
        boolean global = getConfig().getBoolean("settings.default-global-reset", false);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("LootList", new ArrayList<Map<String, Object>>());
        yaml.set(name, root);
        savePoolSettings(name, cooldown, global, false);
        savePool(name, yaml);
        loadData();
        send(player, "pool-created", "<pool>", name);
    }

    private void savePoolSettings(String name, int cooldownSeconds, boolean globalReset, boolean roundDownTime) {
        ConfigurationSection root = getConfig().getConfigurationSection("settings-loot-tables");
        if (root == null) root = getConfig().createSection("settings-loot-tables");
        ConfigurationSection settings = directSection(root, name);
        if (settings == null) settings = root.createSection(name);
        settings.set("Global", globalReset);
        if (!settings.contains("Name")) settings.set("Name", name);
        Map<String, Object> reset = new LinkedHashMap<>();
        int remaining = Math.max(0, cooldownSeconds);
        reset.put("Days", remaining / 86400);
        remaining %= 86400;
        reset.put("Hours", remaining / 3600);
        remaining %= 3600;
        reset.put("Minutes", remaining / 60);
        reset.put("Seconds", remaining % 60);
        settings.set("Reset", reset);
        settings.set("RoundDownTime", roundDownTime);
        saveConfig();
    }

    private void linkRegionBlock(Player player, String pool) {
        if (!pools.containsKey(pool)) {
            send(player, "pool-not-found");
            return;
        }
        Block block = player.getTargetBlock(null, 10);
        String blockKey = blockLinkKey(block);
        if (block == null || blockKey == null) {
            send(player, "target-chest-required");
            return;
        }
        block = canonicalChest(block);
        String region = primaryRegion(block);
        ConfigurationSection world = getOrCreateWorldLinks(block.getWorld().getName());
        ConfigurationSection target = getOrCreateLinkScope(world, region);
        target.set(blockKey, pool);
        saveConfig();
        loadData();
        String scope = region == null
                ? message("scope-world", "<world>", block.getWorld().getName())
                : message("scope-region", "<region>", region);
        send(player, "link-success", "<block>", blockKey, "<scope>", scope, "<pool>", pool);
    }

    private void unlinkRegionBlock(Player player) {
        Block block = player.getTargetBlock(null, 10);
        String blockKey = blockLinkKey(block);
        if (block == null || blockKey == null) {
            send(player, "target-chest-required");
            return;
        }
        block = canonicalChest(block);
        String region = primaryRegion(block);
        ConfigurationSection root = getConfig().getConfigurationSection("links");
        ConfigurationSection world = root == null ? null : directSection(root, block.getWorld().getName());
        ConfigurationSection target = world == null ? null : getLinkScope(world, region);
        if (target != null) target.set(blockKey, null);
        saveConfig();
        loadData();
        String scope = region == null
                ? message("scope-world", "<world>", block.getWorld().getName())
                : message("scope-region", "<region>", region);
        send(player, "unlink-success", "<block>", blockKey, "<scope>", scope);
    }

    private String primaryRegion(Block block) {
        List<String> regions = worldGuard.regions(block.getLocation());
        return regions.isEmpty() ? null : regions.get(0);
    }

    private ConfigurationSection getOrCreateWorldLinks(String worldName) {
        ConfigurationSection root = getConfig().getConfigurationSection("links");
        if (root == null) { root = getConfig().createSection("links"); }
        ConfigurationSection world = directSection(root, worldName);
        return world == null ? root.createSection(worldName) : world;
    }

    private ConfigurationSection getOrCreateLinkScope(ConfigurationSection world, String region) {
        String key = region == null ? "default" : "regions";
        ConfigurationSection scope = directSection(world, key);
        if (scope == null) scope = world.createSection(key);
        if (region == null) return scope;
        ConfigurationSection named = directSection(scope, region);
        return named == null ? scope.createSection(region) : named;
    }

    private ConfigurationSection getLinkScope(ConfigurationSection world, String region) {
        ConfigurationSection scope = directSection(world, region == null ? "default" : "regions");
        if (scope == null || region == null) return scope;
        return directSection(scope, region);
    }

    private ConfigurationSection lootTableSettings(String poolName) {
        ConfigurationSection root = getConfig().getConfigurationSection("settings-loot-tables");
        return root == null ? null : directSection(root, poolName);
    }

    private ConfigurationSection directSection(ConfigurationSection parent, String key) {
        for (String child : parent.getKeys(false)) {
            if (child.equals(key)) return parent.getConfigurationSection(child);
        }
        return null;
    }

    private String materialString(ConfigurationSection section, String material) {
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(material)) return section.getString(key);
        }
        return null;
    }

    private static final class CachedInventory {
        private final Inventory inventory;
        private long expiresAt;

        private CachedInventory(Inventory inventory, long expiresAt) {
            this.inventory = inventory;
            this.expiresAt = expiresAt;
        }
    }

    static final class ResolvedLink {
        private final String poolName;
        private final String title;

        private ResolvedLink(String poolName, String title) {
            this.poolName = poolName;
            this.title = title;
        }
    }

    private static final class OpenInventory {
        private final Block block;
        private final String chestKey;
        private final String poolName;
        private final boolean animateChest;

        private OpenInventory(Block block, String chestKey, String poolName, boolean animateChest) {
            this.block = block;
            this.chestKey = chestKey;
            this.poolName = poolName;
            this.animateChest = animateChest;
        }
    }
}
