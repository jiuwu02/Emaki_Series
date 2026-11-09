package emaki.jiuwu.craft.corelib.config.precheck;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;

public final class ConfigPrecheckLifecycleSupport {

    private ConfigPrecheckLifecycleSupport() {
    }

    public static void register(ConfigPrecheckContributor contributor) {
        if (contributor == null || Texts.isBlank(contributor.module())) {
            return;
        }
        EmakiCoreLibApi.requireService(ConfigPrecheckService.class).registry().register(contributor);
    }

    public static void unregister(String moduleId) {
        if (Texts.isBlank(moduleId)) {
            return;
        }
        EmakiCoreLibApi.requireService(ConfigPrecheckService.class).registry().unregister(moduleId);
    }

    public static void logReport(LogMessages messages, String moduleId) {
        if (messages == null || Texts.isBlank(moduleId)) {
            return;
        }
        EmakiCoreLibPlugin coreLibPlugin = coreLib();
        ConfigPrecheckReport report = EmakiCoreLibApi.requireService(ConfigPrecheckService.class).checkModule(coreLibPlugin.configModel(), moduleId);
        ConfigPrecheckMessages.logReport(messages, moduleId, report);
    }

    private static EmakiCoreLibPlugin coreLib() {
        return EmakiCoreLibApi.requireService(EmakiCoreLibPlugin.class);
    }
}
