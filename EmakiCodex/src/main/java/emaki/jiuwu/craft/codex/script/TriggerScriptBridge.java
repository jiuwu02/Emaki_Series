package emaki.jiuwu.craft.codex.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.api.AdvancementTriggerRegistration;
import emaki.jiuwu.craft.codex.apiimpl.DefaultCodexExtensions;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;
import emaki.jiuwu.craft.corelib.script.host.ScriptLoadReport;

public final class TriggerScriptBridge {

    private static final String BINDING_NAME = "triggers";
    private static final String EXAMPLE_RESOURCE = "scripts/triggers/example_trigger.js";
    private static final String EXAMPLE_TARGET = "triggers/example_trigger.js";

    private final EmakiCodexPlugin plugin;
    private final Path scriptsDirectory;
    private final DefaultCodexExtensions extensions;
    private final List<AdvancementTriggerRegistration> registrations = new CopyOnWriteArrayList<>();
    private volatile ScriptHost host;

    public TriggerScriptBridge(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
        this.scriptsDirectory = plugin.dataPath("scripts");
        this.extensions = new DefaultCodexExtensions(plugin);
    }

    public void loadFromConfig() {
        ScriptHostSettings settings = plugin.appConfig().scripts();
        if (!settings.enabled()) {
            plugin.messageService().info("console.script_disabled");
            return;
        }
        if (!ScriptHost.isEngineAvailable()) {
            plugin.messageService().warning("console.script_engine_unavailable");
            return;
        }
        releaseExampleScript();
        TriggerScriptBinding binding = new TriggerScriptBinding(this);
        ScriptHost created = new ScriptHost(plugin, scriptsDirectory, settings, Map.of(BINDING_NAME, binding));
        binding.bindHost(created);
        host = created;
        report(created.load());
    }

    public void reload() {
        closeHandles();
        ScriptHost current = host;
        if (current == null) {
            loadFromConfig();
            return;
        }
        if (!plugin.appConfig().scripts().enabled()) {
            closeHost(current);
            plugin.messageService().info("console.script_disabled");
            return;
        }
        report(current.reload());
    }

    public void close() {
        closeHandles();
        closeHost(host);
    }

    void registerScriptTrigger(Value payload, ScriptHost sourceHost) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            plugin.messageService().warning("console.script_register_invalid_payload");
            return;
        }
        Object idRaw = memberOf(payload, "id");
        Object priorityRaw = memberOf(payload, "priority");
        Object advancementsMember = memberOf(payload, "advancements");
        boolean advancementsCallable = advancementsMember instanceof Value function && function.canExecute();
        String violation = TriggerScriptLogic.validatePayload(idRaw, priorityRaw, advancementsCallable);
        if (violation != null) {
            reportViolation(violation, TriggerScriptLogic.normalizeId(idRaw));
            return;
        }
        String id = TriggerScriptLogic.normalizeId(idRaw);
        int priority = TriggerScriptLogic.resolvePriority(priorityRaw);
        ScriptAdvancementTrigger trigger = new ScriptAdvancementTrigger(id, priority,
                plugin.messageService(), sourceHost.callbackRunner(), (Value) advancementsMember);
        try {
            AdvancementTriggerRegistration registration = extensions.registerTrigger(plugin, trigger);
            if (registration == AdvancementTriggerRegistration.noop()) {
                plugin.messageService().warning("console.script_register_failed",
                        Map.of("id", id, "reason", "注册被拒绝"));
                return;
            }
            registrations.add(registration);
        } catch (RuntimeException | LinkageError exception) {
            plugin.messageService().warning("console.script_register_failed",
                    Map.of("id", id, "reason", TriggerScriptLogic.describe(exception)));
        }
    }

    private void reportViolation(String violation, String id) {
        switch (violation) {
            case "id_blank" -> plugin.messageService().warning("console.script_register_id_blank");
            case "advancements_missing" -> plugin.messageService()
                    .warning("console.script_register_advancements_missing", Map.of("id", id));
            default -> plugin.messageService()
                    .warning("console.script_register_priority_invalid", Map.of("id", id));
        }
    }

    private Object memberOf(Value payload, String name) {
        Value member = payload.getMember(name);
        if (member == null || member.isNull()) {
            return null;
        }
        if (member.isString()) {
            return member.asString();
        }
        if (member.isNumber()) {
            return member.asInt();
        }
        return member;
    }

    private void report(ScriptLoadReport report) {
        plugin.messageService().info("console.script_loaded",
                Map.of("loaded", report.filesLoaded(), "skipped", report.filesSkipped()));
        for (ScriptLoadReport.ScriptLoadError error : report.errors()) {
            plugin.messageService().warning("console.script_file_error",
                    Map.of("file", error.file(), "reason", error.message()));
        }
    }

    private void closeHandles() {
        List<AdvancementTriggerRegistration> current = List.copyOf(registrations);
        registrations.clear();
        for (AdvancementTriggerRegistration registration : current) {
            try {
                registration.close();
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning(
                        "EmakiCodex 脚本触发器注销失败：" + TriggerScriptLogic.describe(exception));
            }
        }
    }

    private void closeHost(ScriptHost target) {
        host = null;
        if (target != null) {
            target.close();
        }
    }

    private void releaseExampleScript() {
        if (Files.isDirectory(scriptsDirectory)) {
            return;
        }
        Path target = scriptsDirectory.resolve(EXAMPLE_TARGET);
        try (InputStream resource = plugin.getResource(EXAMPLE_RESOURCE)) {
            if (resource == null) {
                plugin.messageService().warning("console.script_release_failed",
                        Map.of("reason", "内置资源缺失"));
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(resource, target);
        } catch (IOException | RuntimeException exception) {
            plugin.messageService().warning("console.script_release_failed",
                    Map.of("reason", TriggerScriptLogic.describe(exception)));
        }
    }
}
