package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class MessageService extends AbstractMessageService {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#66D9EF:#7B7BFF>Emaki Attribute</gradient> ]</gray>";

    private final LanguageLoader languageLoader;
    private final Supplier<AttributeConfig> configSupplier;

    public MessageService(EmakiAttributePlugin plugin,
                          LanguageLoader languageLoader,
                          Supplier<AttributeConfig> configSupplier) {
        super(plugin, DEFAULT_PREFIX);
        this.languageLoader = languageLoader;
        this.configSupplier = configSupplier;
    }

    @Override
    protected String resolveMessage(String key) {
        return languageLoader == null ? key : languageLoader.getMessage(key);
    }

    @Override
    protected String resolveMessage(String key, Map<String, ?> replacements) {
        return languageLoader == null ? key : languageLoader.getMessage(key, replacements);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> replacements) {
        sendRaw(sender, message(key, replacements));
    }

    public void sendRaw(CommandSender sender, String text) {
        sendPrefixed(sender, text);
    }

    public void sendComponent(CommandSender sender, Component component) {
        if (sender == null || component == null) {
            return;
        }
        sender.sendMessage(component);
    }
}
