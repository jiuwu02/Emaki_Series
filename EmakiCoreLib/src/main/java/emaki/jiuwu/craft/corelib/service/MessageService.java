package emaki.jiuwu.craft.corelib.service;

import java.util.Map;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;

public final class MessageService extends AbstractMessageService {

    private static final String DEFAULT_PREFIX = "<gray>[<gold>Emaki CoreLib</gold>]</gray>";

    private final LanguageLoader languageLoader;

    public MessageService(EmakiCoreLibPlugin plugin, LanguageLoader languageLoader) {
        super(plugin, DEFAULT_PREFIX);
        this.languageLoader = languageLoader;
    }

    @Override
    protected String resolveMessage(String key) {
        return languageLoader == null ? key : languageLoader.getMessage(key);
    }

    @Override
    protected String resolveMessage(String key, Map<String, ?> replacements) {
        return languageLoader == null ? key : languageLoader.getMessage(key, replacements);
    }

    @Override
    protected boolean includePrefixInLogs() {
        return true;
    }
}
