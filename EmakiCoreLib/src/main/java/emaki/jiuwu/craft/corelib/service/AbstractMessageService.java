package emaki.jiuwu.craft.corelib.service;

import java.util.Objects;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public class AbstractMessageService implements LogMessages {

    private final JavaPlugin plugin;
    private final String defaultPrefix;
    private final Function<String, String> messageResolver;
    private final BiFunction<String, Map<String, ?>, String> replacementResolver;
    private final boolean includePrefixInLogs;

    protected AbstractMessageService(JavaPlugin plugin, String defaultPrefix) {
        this(plugin, defaultPrefix, null, null, false);
    }

    public AbstractMessageService(JavaPlugin plugin,
            String defaultPrefix,
            Function<String, String> messageResolver,
            BiFunction<String, Map<String, ?>, String> replacementResolver) {
        this(plugin, defaultPrefix, messageResolver, replacementResolver, false);
    }

    public AbstractMessageService(JavaPlugin plugin,
            String defaultPrefix,
            Function<String, String> messageResolver,
            BiFunction<String, Map<String, ?>, String> replacementResolver,
            boolean includePrefixInLogs) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultPrefix = Texts.toStringSafe(defaultPrefix);
        this.messageResolver = messageResolver;
        this.replacementResolver = replacementResolver;
        this.includePrefixInLogs = includePrefixInLogs;
    }

    @Override
    public final String message(String key) {
        return resolveMessage(key);
    }

    @Override
    public final String message(String key, Map<String, ?> replacements) {
        return resolveMessage(key, replacements == null ? Map.of() : replacements);
    }

    public final String messageOrFallback(String key, String fallback) {
        if (Texts.isBlank(key)) {
            return fallback;
        }
        String value = message(key);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
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

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> replacements) {
        sendRaw(sender, message(key, replacements == null ? Map.of() : replacements));
    }

    public void sendRaw(CommandSender sender, String text) {
        sendPrefixed(sender, text);
    }

    public void sendComponent(CommandSender sender, Component component) {
        if (sender == null || component == null) {
            return;
        }
        AdventureSupport.sendMessage(plugin(), sender, component);
    }

    protected String resolveMessage(String key) {
        if (messageResolver == null) {
            return key;
        }
        return messageResolver.apply(key);
    }

    protected String resolveMessage(String key, Map<String, ?> replacements) {
        if (replacementResolver != null) {
            return replacementResolver.apply(key, replacements);
        }
        return Texts.formatTemplate(resolveMessage(key), replacements);
    }

    protected boolean includePrefixInLogs() {
        return includePrefixInLogs;
    }

    protected final JavaPlugin plugin() {
        return plugin;
    }

    protected final void sendPrefixed(CommandSender sender, String text) {
        if (sender == null || Texts.isBlank(text)) {
            return;
        }
        AdventureSupport.sendMessage(plugin(), sender, render(withPrefix(text)));
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
