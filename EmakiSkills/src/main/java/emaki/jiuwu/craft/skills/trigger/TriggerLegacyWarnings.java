package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;

final class TriggerLegacyWarnings {

    private TriggerLegacyWarnings() {
    }

    static void warn(JavaPlugin plugin, String sourceId) {
        if (plugin instanceof LogMessagesProvider provider) {
            LogMessages messages = provider.messageService();
            if (messages != null) {
                messages.warning("console.trigger_legacy_cancelled_dispatch_deprecated",
                        Map.of("source", sourceId));
            }
        }
    }
}
