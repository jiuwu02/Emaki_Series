package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageService {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#66D9EF:#7B7BFF>Emaki Attribute</gradient> ]</gray>";

    private final EmakiAttributePlugin plugin;
    private final LanguageLoader languageLoader;
    private final Supplier<AttributeConfig> configSupplier;

    public MessageService(EmakiAttributePlugin plugin,
                          LanguageLoader languageLoader,
                          Supplier<AttributeConfig> configSupplier) {
        this.plugin = plugin;
        this.languageLoader = languageLoader;
        this.configSupplier = configSupplier;
    }

    public String message(String key) {
        return languageLoader == null ? key : languageLoader.getMessage(key);
    }

    public String message(String key, Map<String, ?> replacements) {
        return languageLoader == null ? key : languageLoader.getMessage(key, replacements);
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
        sender.sendMessage(render(withPrefix(text)));
    }

    public void sendComponent(CommandSender sender, Component component) {
        if (sender == null || component == null) {
            return;
        }
        sender.sendMessage(component);
    }

    public Component render(String text) {
        return MiniMessages.parse(text);
    }

    public void info(String key) {
        info(key, Map.of());
    }

    public void info(String key, Map<String, ?> replacements) {
        log(Level.INFO, message(key, replacements));
    }

    public void warning(String key) {
        warning(key, Map.of());
    }

    public void warning(String key, Map<String, ?> replacements) {
        log(Level.WARNING, message(key, replacements));
    }

    public void severe(String key) {
        severe(key, Map.of());
    }

    public void severe(String key, Map<String, ?> replacements) {
        log(Level.SEVERE, message(key, replacements));
    }

    public void debug(Player player, String key, Map<String, ?> replacements) {
        AttributeConfig config = configSupplier == null ? null : configSupplier.get();
        if (config != null && config.debug()) {
            send(player, key, replacements);
        }
    }

    private String withPrefix(String text) {
        String prefix = message("general.prefix");
        if (Texts.isBlank(prefix) || "general.prefix".equals(prefix)) {
            prefix = DEFAULT_PREFIX;
        }
        String normalizedText = Texts.toStringSafe(text);
        return prefix + (prefix.endsWith(" ") ? "" : " ") + normalizedText;
    }

    private void log(Level level, String text) {
        if (Texts.isBlank(text)) {
            return;
        }
        plugin.getLogger().log(level, MiniMessages.plain(render(text)));
    }
}
