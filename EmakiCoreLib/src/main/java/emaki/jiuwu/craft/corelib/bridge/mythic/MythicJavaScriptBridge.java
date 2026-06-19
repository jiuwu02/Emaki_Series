package emaki.jiuwu.craft.corelib.bridge.mythic;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.script.ScriptServerApi.ScriptEntityApi;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;

public final class MythicJavaScriptBridge implements Listener {

    private final EmakiCoreLibPlugin plugin;

    public MythicJavaScriptBridge(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String name = Texts.normalizeId(event.getMechanicName());
        if (!isJavaScriptMechanic(name) || event.getConfig() == null) {
            return;
        }
        SkillExecutor executor = MythicBukkit.inst().getSkillManager();
        File sourceFile = new File(plugin.getDataFolder(), "mythic/" + name + ".yml");
        event.register(new JavaScriptSkillMechanic(plugin, executor, sourceFile, name, event.getConfig()));
    }

    private boolean isJavaScriptMechanic(String name) {
        return name.equals("emaki_js") || name.equals("corelib_js") || name.equals("emakicorelib_js");
    }

    @MythicMechanic(
            name = "emaki_js",
            aliases = {"corelib_js", "emakicorelib_js"},
            author = "Emaki",
            description = "Execute an EmakiCoreLib JavaScript function from MythicMobs.",
            version = "1.0.0",
            premium = false
    )
    public static final class JavaScriptSkillMechanic extends SkillMechanic {

        private final EmakiCoreLibPlugin plugin;

        public JavaScriptSkillMechanic(EmakiCoreLibPlugin plugin,
                SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config) {
            super(executor, file, mechanicName, config);
            this.plugin = plugin;
        }

        @Override
        public boolean executeSkills(SkillMetadata metadata) {
            if (plugin == null || plugin.javaScriptService() == null || !plugin.javaScriptService().enabled()) {
                return false;
            }
            String script = config.getString("script", config.getString("file", ""));
            String function = config.getString("function", config.getString("fn", "execute"));
            if (Texts.isBlank(script)) {
                plugin.messageService().warning("console.mythic_js_script_missing", Map.of("mechanic", getTypeName()));
                return false;
            }
            ScriptConfig scriptConfig = plugin.configModel().scriptConfig();
            Map<String, Object> parameters = configParameters(metadata);
            ScriptExecutionResult result = plugin.javaScriptService().invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    script,
                    function,
                    List.of(new ScriptMythicMetadataApi(metadata, getTypeName()), parameters),
                    Map.of("extension", "mythic", "mechanic", getTypeName(), "script", script),
                    scriptConfig.clampTimeoutMillis(resolveTimeout(scriptConfig.engine().defaultTimeoutMillis())),
                    false
            ));
            return result != null && result.success() && !result.skipped();
        }

        private long resolveTimeout(long fallback) {
            String raw = config.getString("timeout", "");
            if (Texts.isBlank(raw)) {
                return fallback;
            }
            return Numbers.tryParseLong(raw, fallback);
        }

        private Map<String, Object> configParameters(SkillMetadata metadata) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("mechanic", getTypeName());
            put(parameters, "script", config.getString("script", config.getString("file", "")));
            put(parameters, "function", config.getString("function", config.getString("fn", "execute")));
            put(parameters, "damage", config.getString("damage", null));
            put(parameters, "base", config.getString("base", null));
            put(parameters, "damage_type", config.getString("damage_type", null));
            put(parameters, "type", config.getString("type", null));
            if (metadata != null && metadata.getParameters() != null) {
                parameters.putAll(metadata.getParameters());
            }
            return parameters;
        }

        private void put(Map<String, Object> parameters, String key, String value) {
            if (Texts.isNotBlank(value)) {
                parameters.put(key, value);
            }
        }
    }

    public static final class ScriptMythicMetadataApi {

        private final SkillMetadata metadata;
        private final String mechanicName;

        ScriptMythicMetadataApi(SkillMetadata metadata, String mechanicName) {
            this.metadata = metadata;
            this.mechanicName = mechanicName == null ? "" : mechanicName;
        }

        @HostAccess.Export
        public String skillName() {
            return mechanicName;
        }

        @HostAccess.Export
        public String mechanic() {
            return mechanicName;
        }

        @HostAccess.Export
        public double power() {
            return metadata == null ? 0D : metadata.getPower();
        }

        @HostAccess.Export
        public String cause() {
            return metadata == null || metadata.getCause() == null ? "" : metadata.getCause().name();
        }

        @HostAccess.Export
        public ScriptEntityApi caster() {
            return new ScriptEntityApi(resolveEntity(metadata == null || metadata.getCaster() == null ? null : metadata.getCaster().getEntity()));
        }

        @HostAccess.Export
        public ScriptEntityApi trigger() {
            return new ScriptEntityApi(resolveEntity(metadata == null ? null : metadata.getTrigger()));
        }

        @HostAccess.Export
        public List<ScriptEntityApi> entityTargets() {
            if (metadata == null || metadata.getEntityTargets() == null) {
                return List.of();
            }
            java.util.ArrayList<ScriptEntityApi> result = new java.util.ArrayList<>();
            for (AbstractEntity target : metadata.getEntityTargets()) {
                result.add(new ScriptEntityApi(resolveEntity(target)));
            }
            return List.copyOf(result);
        }

        @HostAccess.Export
        public ScriptEntityApi firstTarget() {
            List<ScriptEntityApi> targets = entityTargets();
            if (!targets.isEmpty()) {
                return targets.get(0);
            }
            return trigger();
        }

        @HostAccess.Export
        public Map<String, String> parameters() {
            return metadata == null || metadata.getParameters() == null ? Map.of() : Map.copyOf(metadata.getParameters());
        }

        private org.bukkit.entity.Entity resolveEntity(AbstractEntity abstractEntity) {
            return abstractEntity == null ? null : abstractEntity.getBukkitEntity();
        }
    }
}
