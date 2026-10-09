package emaki.jiuwu.craft.item.script;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;
import emaki.jiuwu.craft.corelib.script.host.ScriptLoadReport;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.api.effect.ItemEffectRegistration;
import emaki.jiuwu.craft.item.config.ScriptSettings;
import emaki.jiuwu.craft.item.service.EmakiItemEffectRegistry;

public final class ItemScriptBridge implements AutoCloseable {

    private static final String SCRIPTS_DIRECTORY = "scripts";
    private static final String BINDING_NAME = "items";

    private final EmakiItemPlugin plugin;
    private final ItemScriptBinding binding;
    private final List<ItemEffectRegistration> registrations = new ArrayList<>();
    private ScriptHost host;

    public ItemScriptBridge(EmakiItemPlugin plugin) {
        this.plugin = plugin;
        this.binding = new ItemScriptBinding(this);
    }

    public synchronized void loadFromConfig() {
        ScriptSettings settings = plugin.appConfig().scripts();
        if (!settings.enabled()) {
            plugin.messageService().info("console.scripts.disabled");
            return;
        }
        if (!ScriptHost.isEngineAvailable()) {
            plugin.messageService().warning("console.scripts.engine_unavailable");
            return;
        }
        host = createHost(settings);
        report(host.load());
    }

    public synchronized void reload() {
        closeRegistrations();
        ScriptSettings settings = plugin.appConfig().scripts();
        if (!settings.enabled()) {
            closeHost();
            plugin.messageService().info("console.scripts.disabled");
            return;
        }
        if (!ScriptHost.isEngineAvailable()) {
            closeHost();
            plugin.messageService().warning("console.scripts.engine_unavailable");
            return;
        }
        if (host == null) {
            host = createHost(settings);
        }
        report(host.reload());
    }

    @Override
    public synchronized void close() {
        closeRegistrations();
        closeHost();
    }

    synchronized void registerEffectType(Value payload) {
        String reportedId = ItemScriptPayloads.tryExtractId(payload);
        try {
            ItemScriptPayload spec = ItemScriptPayloads.from(payload);
            ScriptItemEffectType type = new ScriptItemEffectType(
                    spec, host.callbackRunner(), plugin.getLogger());
            ItemEffectRegistration registration =
                    EmakiItemApi.extensions().registerEffectType(plugin, type);
            EmakiItemEffectRegistry registry = plugin.effectRegistry();
            if (registration == null || registry == null || registry.find(spec.id()) != type) {
                plugin.messageService().warning("console.scripts.register_error",
                        Map.of("id", spec.id(), "error", "注册被拒绝"));
                return;
            }
            registrations.add(registration);
        } catch (IllegalArgumentException exception) {
            plugin.messageService().warning("console.scripts.register_error",
                    Map.of("id", reportedId == null ? "unknown" : reportedId,
                            "error", String.valueOf(exception.getMessage())));
        }
    }

    private ScriptHost createHost(ScriptSettings settings) {
        Path scriptsDirectory = plugin.getDataFolder().toPath().resolve(SCRIPTS_DIRECTORY);
        return new ScriptHost(plugin, scriptsDirectory,
                new ScriptHostSettings(true, settings.timeoutMs()),
                Map.of(BINDING_NAME, binding));
    }

    private void closeRegistrations() {
        for (ItemEffectRegistration registration : registrations) {
            registration.close();
        }
        registrations.clear();
    }

    private void closeHost() {
        if (host != null) {
            host.close();
            host = null;
        }
    }

    private void report(ScriptLoadReport report) {
        for (ScriptLoadReport.ScriptLoadError error : report.errors()) {
            plugin.messageService().warning("console.scripts.file_error",
                    Map.of("file", error.file(), "error", error.message()));
        }
        plugin.messageService().info("console.scripts.loaded",
                Map.of("loaded", report.filesLoaded(), "errors", report.filesSkipped()));
    }
}
