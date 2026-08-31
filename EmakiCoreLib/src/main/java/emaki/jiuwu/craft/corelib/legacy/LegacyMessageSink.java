package emaki.jiuwu.craft.corelib.legacy;

import java.util.Map;

import org.bukkit.command.CommandSender;

public interface LegacyMessageSink {

    String message(String key);

    String message(String key, Map<String, ?> replacements);

    void send(CommandSender sender, String key);

    void sendRaw(CommandSender sender, String text);
}
