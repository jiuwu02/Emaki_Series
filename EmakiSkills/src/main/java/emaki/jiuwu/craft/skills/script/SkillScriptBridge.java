package emaki.jiuwu.craft.skills.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;
import emaki.jiuwu.craft.corelib.script.host.ScriptLoadReport;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillDefinitionRegistration;
import emaki.jiuwu.craft.skills.config.AppConfig;

public final class SkillScriptBridge implements AutoCloseable {

    private static final String BINDING_NAME = "skills";
    private static final String EXAMPLE_RESOURCE = "scripts/skills/example_script_skill.js";
    private static final String EMPTY_ID_REASON = "console.scripts.reason.blank_id";
    private static final String REGISTRY_INACTIVE_REASON = "console.scripts.reason.registry_inactive";
    private static final String REGISTRATION_REJECTED_REASON = "console.scripts.reason.registration_rejected";

    private final EmakiSkillsPlugin plugin;
    private final SkillScriptBinding binding;
    private final CopyOnWriteArrayList<SkillDefinitionRegistration> registrations = new CopyOnWriteArrayList<>();
    private final List<String[]> registerErrors = new ArrayList<>();
    private ScriptHost host;

    public SkillScriptBridge(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
        this.binding = new SkillScriptBinding(this);
    }

    public void loadFromConfig() {
        AppConfig.ScriptSettings settings = plugin.appConfig().scripts();
        if (!settings.enabled()) {
            reportDisabled();
            return;
        }
        if (!ScriptHost.isEngineAvailable()) {
            plugin.messageService().warning("console.scripts.engine_unavailable");
            return;
        }
        registerErrors.clear();
        seedExampleScript();
        if (host == null) {
            host = new ScriptHost(plugin, scriptsDirectory(),
                    new ScriptHostSettings(true, settings.timeoutMs()),
                    Map.of(BINDING_NAME, binding));
        }
        report(host.load());
    }

    public void reload() {
        if (host == null) {
            loadFromConfig();
            return;
        }
        AppConfig.ScriptSettings settings = plugin.appConfig().scripts();
        if (!settings.enabled()) {
            close();
            reportDisabled();
            return;
        }
        registerErrors.clear();
        closeRegistrations();
        report(host.reload());
    }

    void register(Value payload) {
        ScriptSkillDefinition definition = ScriptSkillDefinition.create(SkillScriptPayloadReader.read(payload));
        if (definition == null) {
            addRegisterError("", EMPTY_ID_REASON);
            return;
        }
        if (!plugin.isEnabled() || plugin.externalSkillDefinitionRegistry() == null) {
            addRegisterError(definition.id(), REGISTRY_INACTIVE_REASON);
            return;
        }
        SkillDefinitionRegistration registration =
                plugin.externalSkillDefinitionRegistry().register(plugin, definition);
        if (registration == null || registration == SkillDefinitionRegistration.noop()) {
            addRegisterError(definition.id(), REGISTRATION_REJECTED_REASON);
            return;
        }
        registrations.add(registration);
    }

    @Override
    public void close() {
        closeRegistrations();
        if (host != null) {
            host.close();
            host = null;
        }
    }

    private void closeRegistrations() {
        for (SkillDefinitionRegistration registration : registrations) {
            registration.close();
        }
        registrations.clear();
    }

    private void report(ScriptLoadReport report) {
        MessageService messages = plugin.messageService();
        messages.info("console.scripts.loaded", Map.of(
                "files", String.valueOf(report.filesLoaded()),
                "errors", String.valueOf(report.filesSkipped() + registerErrors.size())));
        for (ScriptLoadReport.ScriptLoadError error : report.errors()) {
            messages.warning("console.scripts.file_error",
                    Map.of("file", error.file(), "error", error.message()));
        }
        for (String[] error : registerErrors) {
            messages.warning("console.scripts.register_error",
                    Map.of("id", error[0], "error", messages.message(error[1])));
        }
        registerErrors.clear();
    }

    private void reportDisabled() {
        plugin.getLogger().fine(plugin.messageService().message("console.scripts.disabled"));
    }

    private void addRegisterError(String id, String reason) {
        registerErrors.add(new String[]{id, reason});
    }

    private Path scriptsDirectory() {
        return plugin.getDataFolder().toPath().resolve("scripts");
    }

    private void seedExampleScript() {
        if (Files.isDirectory(scriptsDirectory())) {
            return;
        }
        try {
            plugin.saveResource(EXAMPLE_RESOURCE, false);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(plugin.messageService().messageOrFallback(
                    "console.scripts.example_release_failed",
                    Map.of("path", EXAMPLE_RESOURCE, "error", String.valueOf(exception.getMessage())),
                    "释放示例技能脚本失败: " + EXAMPLE_RESOURCE));
        }
    }
}
