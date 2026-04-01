package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;

import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.LanguageLoader;
import net.kyori.adventure.text.Component;

public final class MessageService extends AbstractMessageService {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F2C46D:#C9703D>Emaki Forge</gradient> ]</gray>";

    private final LanguageLoader languageLoader;
    private final Supplier<AppConfig> configSupplier;

    public MessageService(EmakiForgePlugin plugin,
            LanguageLoader languageLoader,
            Supplier<AppConfig> configSupplier) {
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
