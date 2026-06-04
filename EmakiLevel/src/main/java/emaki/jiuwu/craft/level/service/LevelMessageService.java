package emaki.jiuwu.craft.level.service;

import java.io.File;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class LevelMessageService implements LogMessages {

    private final JavaPlugin plugin;
    private String language = "zh_CN";
    private YamlSection messages = YamlFiles.load("");

    public LevelMessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.language = Texts.isBlank(language) ? "zh_CN" : Texts.trim(language);
        File file = plugin.getDataFolder().toPath().resolve("lang").resolve(this.language + ".yml").toFile();
        messages = YamlFiles.load(file);
    }

    @Override
    public String message(String key) {
        return message(key, Map.of());
    }

    @Override
    public String message(String key, Map<String, ?> replacements) {
        String raw = messages.getString(key, key);
        String prefix = messages.getString("prefix", "<gray>[<gold>EmakiLevel</gold>]</gray> ");
        Map<String, Object> merged = new java.util.LinkedHashMap<>();
        merged.put("prefix", prefix);
        if (replacements != null) {
            merged.putAll(replacements);
        }
        return Texts.formatTemplate(raw, merged);
    }

    @Override
    public Component render(String text) {
        return MiniMessages.parse(text);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> replacements) {
        AdventureSupport.sendMiniMessage(plugin, sender, message(key, replacements));
    }

    public void sendRaw(CommandSender sender, String text) {
        AdventureSupport.sendMiniMessage(plugin, sender, text);
    }

    @Override
    public void info(String key) {
        plugin.getLogger().info(Texts.stripMiniTags(message(key)));
    }

    @Override
    public void info(String key, Map<String, ?> replacements) {
        plugin.getLogger().info(Texts.stripMiniTags(message(key, replacements)));
    }

    @Override
    public void warning(String key) {
        plugin.getLogger().warning(Texts.stripMiniTags(message(key)));
    }

    @Override
    public void warning(String key, Map<String, ?> replacements) {
        plugin.getLogger().warning(Texts.stripMiniTags(message(key, replacements)));
    }

    @Override
    public void severe(String key) {
        plugin.getLogger().severe(Texts.stripMiniTags(message(key)));
    }

    @Override
    public void severe(String key, Map<String, ?> replacements) {
        plugin.getLogger().severe(Texts.stripMiniTags(message(key, replacements)));
    }

    public String language() {
        return language;
    }
}
