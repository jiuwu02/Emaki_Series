package emaki.jiuwu.craft.corelib.service;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.text.Texts;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService extends AbstractMessageService {

    private static final String DEFAULT_PREFIX = "<gray>[<gold>Emaki CoreLib</gold>]</gray>";

    public MessageService(EmakiCoreLibPlugin plugin, LanguageLoader languageLoader) {
        this(plugin, languageLoader, DEFAULT_PREFIX, true);
    }

    public MessageService(JavaPlugin plugin, LanguageLoader languageLoader, String defaultPrefix) {
        this(plugin, languageLoader, defaultPrefix, false);
    }

    public MessageService(JavaPlugin plugin,
            LanguageLoader languageLoader,
            String defaultPrefix,
            boolean includePrefixInLogs) {
        this(
                plugin,
                defaultPrefix,
                languageLoader == null ? null : languageLoader::getMessage,
                languageLoader == null ? null : languageLoader::getMessage,
                includePrefixInLogs
        );
    }

    public MessageService(JavaPlugin plugin,
            String defaultPrefix,
            Function<String, String> messageResolver,
            BiFunction<String, Map<String, ?>, String> replacementResolver,
            boolean includePrefixInLogs) {
        super(
                plugin,
                defaultPrefix,
                messageResolver == null ? key -> key : messageResolver,
                replacementResolver == null
                        ? (key, replacements) -> Texts.formatTemplate(messageResolver == null ? key : messageResolver.apply(key), replacements)
                        : replacementResolver,
                includePrefixInLogs
        );
    }
}
