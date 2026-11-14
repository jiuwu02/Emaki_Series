package emaki.jiuwu.craft.forge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.command.PaperCommandAdapter;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.action.ForgeStageRegistrar;
import emaki.jiuwu.craft.forge.api.EmakiForgeApi;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.config.ForgeConfigPrecheckContributor;
import emaki.jiuwu.craft.forge.editor.ForgeEditorModule;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.papi.ForgePlaceholderExpansion;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;
import emaki.jiuwu.craft.forge.apiimpl.DefaultEmakiForgeApi;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;

final class EmakiForgePluginDelegate {

    private final EmakiForgePlugin ownerRef;

    EmakiForgePluginDelegate(EmakiForgePlugin ownerRef) {
        this.ownerRef = ownerRef;
    }

    void registerCoreShutdownBarrier(EmakiCoreLibPlugin coreLibPlugin, CompletableFuture<Void> shutdownFuture) {
        if (coreLibPlugin == null) {
            ownerRef.getLogger().warning("[shutdown] CoreLib 依赖关闭屏障不可用");
            return;
        }
        try {
            if (!coreLibPlugin.registerDependentShutdown("forge", shutdownFuture)) {
                ownerRef.getLogger().warning("[shutdown] CoreLib 拒绝了 Forge 依赖关闭屏障");
            }
        } catch (Throwable throwable) {
            ownerRef.getLogger().warning("[shutdown] CoreLib 依赖关闭屏障注册失败: "
                    + String.valueOf(throwable.getMessage()));
        }
    }

    void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new ForgeConfigPrecheckContributor(ownerRef));
    }



    EmakiCoreLibPlugin coreLib() {
        return EmakiCoreLibApi.requireService(EmakiCoreLibPlugin.class);
    }

    ChatInputService chatInputService() {
        return ownerRef.chatInputService;
    }

    ForgeEditorModule editorModule() {
        return ownerRef.editorModule;
    }

    EditorService editorService() {
        return ownerRef.editorService;
    }

    DebugCommand debugCommand() {
        return ownerRef.debugCommand;
    }

    ForgeRuntimeMetrics runtimeMetrics() {
        return ownerRef.runtimeMetrics;
    }

    DebugLogger replacementDebugLogger(DebugLogger previous, LanguageLoader nextLanguageLoader) {
        DebugLogger replacement = ownerRef.buildDebugLogger(nextLanguageLoader);
        if (previous == null || !previous.isGlobalEnabled()) {
            return replacement;
        }
        if (previous.trackedPlayers().isEmpty() && previous.enabledModules().isEmpty()) {
            replacement.enableAll();
            return replacement;
        }
        previous.enabledModules().forEach(replacement::enableModule);
        previous.trackedPlayers().forEach(replacement::addPlayer);
        return replacement;
    }

}
