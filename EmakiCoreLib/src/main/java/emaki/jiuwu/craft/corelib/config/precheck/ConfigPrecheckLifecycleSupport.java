package emaki.jiuwu.craft.corelib.config.precheck;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ConfigPrecheckLifecycleSupport {

    private ConfigPrecheckLifecycleSupport() {
    }

    public static void register(ConfigPrecheckContributor contributor) {
        if (contributor == null || Texts.isBlank(contributor.module())) {
            return;
        }
        coreLib().configPrecheckService().registry().register(contributor);
    }

    public static void unregister(String moduleId) {
        if (Texts.isBlank(moduleId)) {
            return;
        }
        coreLib().configPrecheckService().registry().unregister(moduleId);
    }

    public static void logReport(LogMessages messages, String moduleId) {
        if (messages == null || Texts.isBlank(moduleId)) {
            return;
        }
        EmakiCoreLibPlugin coreLibPlugin = coreLib();
        ConfigPrecheckReport report = coreLibPlugin.configPrecheckService().checkModule(coreLibPlugin.configModel(), moduleId);
        ConfigPrecheckMessages.logReport(messages, moduleId, report);
    }

    private static EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }
}
