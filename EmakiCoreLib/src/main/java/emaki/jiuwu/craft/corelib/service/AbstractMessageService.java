package emaki.jiuwu.craft.corelib.service;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public abstract class AbstractMessageService implements LogMessages {

    private final JavaPlugin plugin;
    private final String defaultPrefix;

    protected AbstractMessageService(JavaPlugin plugin, String defaultPrefix) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultPrefix = Texts.toStringSafe(defaultPrefix);
    }

    @Override
    public final String message(String key) {
        return resolveMessage(key);
    }

    @Override
    public final String message(String key, Map<String, ?> replacements) {
        return resolveMessage(key, replacements == null ? Map.of() : replacements);
    }

    @Override
    public Component render(String text) {
        return MiniMessages.parse(text);
    }

    @Override
    public final void info(String key) {
        info(key, Map.of());
    }

    @Override
    public final void info(String key, Map<String, ?> replacements) {
        log(Level.INFO, message(key, replacements));
    }

    @Override
    public final void warning(String key) {
        warning(key, Map.of());
    }

    @Override
    public final void warning(String key, Map<String, ?> replacements) {
        log(Level.WARNING, message(key, replacements));
    }

    @Override
    public final void severe(String key) {
        severe(key, Map.of());
    }

    @Override
    public final void severe(String key, Map<String, ?> replacements) {
        log(Level.SEVERE, message(key, replacements));
    }

    protected abstract String resolveMessage(String key);

    protected abstract String resolveMessage(String key, Map<String, ?> replacements);

    protected boolean includePrefixInLogs() {
        return false;
    }

    protected final JavaPlugin plugin() {
        return plugin;
    }

    protected final void sendPrefixed(CommandSender sender, String text) {
        if (sender == null || Texts.isBlank(text)) {
            return;
        }
        sender.sendMessage(render(withPrefix(text)));
    }

    protected final String withPrefix(String text) {
        String prefix = message("general.prefix");
        if (Texts.isBlank(prefix) || "general.prefix".equals(prefix)) {
            prefix = defaultPrefix;
        }
        String normalizedText = Texts.toStringSafe(text);
        return prefix + (prefix.endsWith(" ") ? "" : " ") + normalizedText;
    }

    private void log(Level level, String text) {
        if (Texts.isBlank(text)) {
            return;
        }
        String loggedText = includePrefixInLogs() ? withPrefix(text) : text;
        plugin.getLogger().log(level, MiniMessages.plain(render(loggedText)));
    }
}
