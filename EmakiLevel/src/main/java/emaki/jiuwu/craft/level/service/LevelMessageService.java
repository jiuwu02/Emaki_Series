package emaki.jiuwu.craft.level.service;

import java.io.File;
import java.util.Map;

import org.bukkit.Bukkit;
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
        Map<String, Object> merged = new java.util.LinkedHashMap<>();
        merged.put("prefix", prefix());
        if (replacements != null) {
            merged.putAll(replacements);
        }
        return Texts.formatTemplate(raw, merged);
    }

    private String prefix() {
        String prefix = resolveText("general.prefix", null);
        if (Texts.isBlank(prefix)) {
            prefix = resolveText("prefix", "<gray>[ <gradient:#7DD3FC:#C084FC>EmakiLevel</gradient> ]</gray>");
        }
        return Texts.toStringSafe(prefix);
    }

    private String withPrefix(String text) {
        String prefix = prefix();
        String normalizedText = Texts.toStringSafe(text);
        return prefix + (prefix.endsWith(" ") ? "" : " ") + normalizedText;
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
        sendConsole(message(key, replacements));
    }

    @Override
    public void warning(String key) {
        warning(key, Map.of());
    }

    @Override
    public void warning(String key, Map<String, ?> replacements) {
        sendConsole(message(key, replacements));
    }

    @Override
    public void severe(String key) {
        severe(key, Map.of());
    }

    @Override
    public void severe(String key, Map<String, ?> replacements) {
        sendConsole(message(key, replacements));
    }

    private void sendConsole(String text) {
        if (Texts.isBlank(text)) {
            return;
        }
        Bukkit.getConsoleSender().sendMessage(MiniMessages.parse(withPrefix(text)));
    }

    public String language() {
        return language;
    }
}
