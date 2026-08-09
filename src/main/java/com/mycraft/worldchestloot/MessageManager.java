package com.mycraft.worldchestloot;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class MessageManager {
    private final File file;
    private final MyCraftWorldChestLoot plugin;
    private YamlConfiguration messages;

    MessageManager(MyCraftWorldChestLoot plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        reload();
    }

    void reload() {
        messages = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource("message.yml");
        if (stream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }
    }

    String text(String key, String... replacements) {
        String value = messages.getString(key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    void send(CommandSender sender, String key, String... replacements) {
        String value = text(key, replacements);
        if (!value.isEmpty()) sender.sendMessage(value);
    }
}
