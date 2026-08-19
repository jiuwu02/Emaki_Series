package emaki.jiuwu.craft.level.service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class LevelMessageService implements LogMessages {

    private final JavaPlugin plugin;
    private String language = "zh_CN";
    private YamlSection messages = YamlFiles.load("");
    private YamlSection fallbackMessages = YamlFiles.load("");

    public LevelMessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.language = Texts.isBlank(language) ? "zh_CN" : Texts.trim(language);
        fallbackMessages = YamlFiles.loadResource(plugin, "lang/" + this.language + ".yml");
        File file = plugin.getDataFolder().toPath().resolve("lang").resolve(this.language + ".yml").toFile();
        messages = YamlFiles.load(file);
    }

    @Override
    public String message(String key) {
        return message(key, Map.of());
    }

    @Override
    public String message(String key, Map<String, ?> replacements) {
        String raw = resolveText(key, key);
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("prefix", prefix());
        if (replacements != null) {
            merged.putAll(replacements);
        }
        return Texts.formatTemplate(raw, merged);
    }

    private String prefix() {
        String prefix = resolveText("general.prefix", null);
        if (Texts.isBlank(prefix)) {
            prefix = resolveText("prefix", "<gray>[ <gradient:#A855F7:#F472B6>EmakiLevel</gradient> ]</gray>");
        }
        return Texts.toStringSafe(prefix);
    }

    private String resolveText(String key, String fallback) {
        String local = nestedString(messages, key);
        if (local != null) {
            return local;
        }
        String bundled = nestedString(fallbackMessages, key);
        return bundled == null ? fallback : bundled;
    }

    private String nestedString(YamlSection section, String key) {
        if (section == null || Texts.isBlank(key)) {
            return null;
        }
        String direct = section.getString(key, null);
        if (direct != null) {
            return direct;
        }
        String[] parts = key.split("\\.");
        YamlSection current = section;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (Texts.isBlank(part)) {
                return null;
            }
            if (index == parts.length - 1) {
                return current.getString(part, null);
            }
            Object nested = current.get(part);
            if (nested instanceof YamlSection nestedSection) {
                current = nestedSection;
            } else {
                return null;
            }
        }
        return null;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> replacements) {
        sendRaw(sender, message(key, replacements));
    }

    public void sendRaw(CommandSender sender, String text) {
        if (sender == null || Texts.isBlank(text)) {
            return;
        }
        sender.sendMessage(MiniMessages.parse(text));
    }

    @Override
    public void info(String key) {
        info(key, Map.of());
    }

    @Override
    public void info(String key, Map<String, ?> replacements) {
        sendConsole(Level.INFO, message(key, replacements));
    }

    @Override
    public void warning(String key) {
        warning(key, Map.of());
    }

    @Override
    public void warning(String key, Map<String, ?> replacements) {
        sendConsole(Level.WARNING, message(key, replacements));
    }

    @Override
    public void severe(String key) {
        severe(key, Map.of());
    }

    @Override
    public void severe(String key, Map<String, ?> replacements) {
        sendConsole(Level.SEVERE, message(key, replacements));
    }

    private void sendConsole(Level level, String text) {
        if (Texts.isBlank(text)) {
            return;
        }
        var component = MiniMessages.parse(text);
        if (level.intValue() >= Level.SEVERE.intValue()) {
            plugin.getComponentLogger().error(component);
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            plugin.getComponentLogger().warn(component);
        } else {
            plugin.getComponentLogger().info(component);
        }
    }

    public String language() {
        return language;
    }
}
