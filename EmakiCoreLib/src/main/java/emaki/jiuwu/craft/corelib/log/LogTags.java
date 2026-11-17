package emaki.jiuwu.craft.corelib.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class LogTags {

    private static final Map<String, Function<String, String>> RESOLVERS = new ConcurrentHashMap<>();

    private static final Map<String, String> TAG_OWNERS = new ConcurrentHashMap<>();

    private static volatile Function<String, String> fallbackResolver;

    private LogTags() {
    }

    public static void register(String pluginName, Function<String, String> resolver) {
        if (pluginName == null || resolver == null) {
            return;
        }
        RESOLVERS.put(pluginName, resolver);
    }

    public static void registerFallback(Function<String, String> resolver) {
        fallbackResolver = resolver;
    }

    public static void registerTags(String pluginName, LogTagType... tags) {
        if (pluginName == null || tags == null) {
            return;
        }
        for (LogTagType tag : tags) {
            if (tag == null || tag.key() == null || tag.key().isBlank()) {
                continue;
            }
            String owner = TAG_OWNERS.putIfAbsent(tag.key(), pluginName);
            if (owner != null && !owner.equals(pluginName)) {
                send(pluginName, null, Level.WARNING, "console.tag_register_conflict",
                        Map.of("key", tag.key(), "owner", owner, "plugin", pluginName), null);
            }
        }
    }

    public static void registerTags(Plugin plugin, LogTagType... tags) {
        registerTags(plugin == null ? null : plugin.getName(), tags);
    }

    public static String head(Plugin plugin, String tag) {
        return head(plugin == null ? null : plugin.getName(), tag);
    }

    public static String head(Logger logger, String tag) {
        return head(EmakiLog.pluginNameOf(logger), tag);
    }

    public static String head(String pluginName, String tag) {
        String key = "tags." + tag;
        Function<String, String> resolver = pluginName == null ? null : RESOLVERS.get(pluginName);
        String value = resolve(resolver, key);
        if (value == null) {
            value = resolve(fallbackResolver, key);
        }
        return value == null ? "[ " + tag + " ]" : value;
    }

    public static String text(Plugin plugin, String langKey, Map<String, ?> replacements) {
        return plugin == null ? "" : resolveText(plugin.getName(), langKey, replacements);
    }

    public static String text(Logger logger, String langKey, Map<String, ?> replacements) {
        return resolveText(EmakiLog.pluginNameOf(logger), langKey, replacements);
    }

    public static String compose(Plugin plugin, LogTagType tag, String langKey, Map<String, ?> replacements) {
        return plugin == null ? "" : compose(plugin.getName(), tag, langKey, replacements);
    }

    public static String compose(Logger logger, LogTagType tag, String langKey, Map<String, ?> replacements) {
        return compose(EmakiLog.pluginNameOf(logger), tag, langKey, replacements);
    }

    public static String compose(String pluginName, LogTagType tag, String langKey, Map<String, ?> replacements) {
        String head = tag == null ? "" : head(pluginName, tag.key());
        String text = resolveText(pluginName, langKey, replacements);
        return text == null || text.isBlank() ? head : (head.isEmpty() ? text : head + " " + text);
    }

    public static void send(Plugin plugin, LogTagType tag, Level level, String langKey) {
        send(plugin, tag, level, langKey, null, null);
    }

    public static void send(Plugin plugin, LogTagType tag, Level level, String langKey, Map<String, ?> replacements) {
        send(plugin, tag, level, langKey, replacements, null);
    }

    public static void send(Plugin plugin, LogTagType tag, Level level, String langKey, Map<String, ?> replacements, Throwable thrown) {
        if (plugin == null) {
            return;
        }
        emit(plugin.getName(), plugin.getLogger(), tag, level, resolveText(plugin.getName(), langKey, replacements), thrown);
    }

    public static void send(Logger logger, LogTagType tag, Level level, String langKey) {
        send(logger, tag, level, langKey, null, null);
    }

    public static void send(Logger logger, LogTagType tag, Level level, String langKey, Map<String, ?> replacements) {
        send(logger, tag, level, langKey, replacements, null);
    }

    public static void send(Logger logger, LogTagType tag, Level level, String langKey, Map<String, ?> replacements, Throwable thrown) {
        String pluginName = EmakiLog.pluginNameOf(logger);
        emit(pluginName, logger, tag, level, resolveText(pluginName, langKey, replacements), thrown);
    }

    public static void send(String pluginName, LogTagType tag, Level level, String langKey, Map<String, ?> replacements, Throwable thrown) {
        emit(pluginName, EmakiLog.of(pluginName), tag, level, resolveText(pluginName, langKey, replacements), thrown);
    }

    public static void sendRaw(Plugin plugin, LogTagType tag, Level level, String message) {
        sendRaw(plugin, tag, level, message, null);
    }

    public static void sendRaw(Plugin plugin, LogTagType tag, Level level, String message, Throwable thrown) {
        if (plugin == null) {
            return;
        }
        emit(plugin.getName(), plugin.getLogger(), tag, level, message, thrown);
    }

    public static void sendRaw(Logger logger, LogTagType tag, Level level, String message) {
        sendRaw(logger, tag, level, message, null);
    }

    public static void sendRaw(Logger logger, LogTagType tag, Level level, String message, Throwable thrown) {
        emit(EmakiLog.pluginNameOf(logger), logger, tag, level, message, thrown);
    }

    public static void sendRaw(String pluginName, LogTagType tag, Level level, String message, Throwable thrown) {
        emit(pluginName, EmakiLog.of(pluginName), tag, level, message, thrown);
    }

    private static void emit(String pluginName, Logger logger, LogTagType tag, Level level, String text, Throwable thrown) {
        if (logger == null || level == null) {
            return;
        }
        String head = tag == null ? "" : head(pluginName, tag.key());
        String composed = text == null || text.isBlank()
                ? head
                : (head.isEmpty() ? text : head + " " + text);
        if (thrown == null) {
            logger.log(level, composed);
        } else {
            logger.log(level, composed, thrown);
        }
    }

    private static String resolveText(String pluginName, String langKey, Map<String, ?> replacements) {
        if (langKey == null) {
            return "";
        }
        Function<String, String> resolver = pluginName == null ? null : RESOLVERS.get(pluginName);
        String template = resolve(resolver, langKey);
        if (template == null) {
            template = resolve(fallbackResolver, langKey);
        }
        if (template == null) {
            template = langKey;
        }
        return replacements == null || replacements.isEmpty() ? template : Texts.formatTemplate(template, replacements);
    }

    private static String resolve(Function<String, String> resolver, String key) {
        if (resolver == null) {
            return null;
        }
        String value = resolver.apply(key);
        return value == null || value.isBlank() || key.equals(value) ? null : value;
    }
}
