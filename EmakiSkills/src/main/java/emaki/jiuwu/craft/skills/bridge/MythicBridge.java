package emaki.jiuwu.craft.skills.bridge;

import java.util.Map;
import java.util.List;
import java.util.logging.Level;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;

import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;

public final class MythicBridge {

    private final JavaPlugin plugin;
    private final LogMessages messages;
    private boolean available;
    private BukkitAPIHelper apiHelper;

    public MythicBridge(JavaPlugin plugin, LogMessages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void init() {
        available = false;
        apiHelper = null;
        try {
            if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) {
                info("console.mythic_bridge_unavailable");
                return;
            }
            apiHelper = MythicBukkit.inst().getAPIHelper();
            available = true;
            info("console.mythic_bridge_ready");
        } catch (NoClassDefFoundError _) {
            info("console.mythic_bridge_class_unavailable");
        } catch (Exception exception) {
            warning("console.mythic_bridge_init_failed", Map.of(
                    "error", errorMessage(exception)
            ), exception);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean skillExists(String mythicSkillId) {
        if (!available || apiHelper == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        try {
            return apiHelper.getMythicMob(mythicSkillId) != null
                    || MythicBukkit.inst().getSkillManager().getSkill(mythicSkillId).isPresent();
        } catch (Exception exception) {
            warning("console.mythic_bridge_skill_lookup_failed", Map.of(
                    "skill", String.valueOf(mythicSkillId),
                    "error", errorMessage(exception)
            ), exception);
            return false;
        }
    }

    public enum CastStatus {
        SUCCESS,
        SKILL_NOT_FOUND,
        CONDITIONS_UNMET,
        UNAVAILABLE,
        ERROR
    }

    public record CastResult(CastStatus status, String detail) {
    }

    public CastResult castSkillDetailed(Entity caster,
            String mythicSkillId,
            Entity trigger,
            Location origin,
            List<Entity> entityTargets,
            List<Location> locationTargets,
            Map<String, String> variables) {
        if (!available || apiHelper == null || caster == null
                || mythicSkillId == null || mythicSkillId.isBlank()) {
            return new CastResult(CastStatus.UNAVAILABLE, "");
        }
        try {
            return MythicCastExecutor.cast(apiHelper, caster, mythicSkillId, trigger, origin,
                    entityTargets, locationTargets, variables);
        } catch (NoClassDefFoundError | Exception exception) {
            warning("console.mythic_bridge_cast_failed", Map.of(
                    "skill", String.valueOf(mythicSkillId),
                    "error", errorMessage(exception)
            ), exception);
            try {
                boolean legacy = apiHelper.castSkill(caster, mythicSkillId, trigger, origin,
                        entityTargets == null ? List.of() : entityTargets,
                        locationTargets == null ? List.of() : locationTargets,
                        1.0F, metadataConsumer(variables == null ? Map.of() : variables));
                return legacy
                        ? new CastResult(CastStatus.SUCCESS, "legacy_fallback")
                        : new CastResult(CastStatus.ERROR, "legacy_fallback");
            } catch (Exception legacyException) {
                return new CastResult(CastStatus.ERROR, errorMessage(legacyException));
            }
        }
    }

    public CastResult castSkillForInvocation(Player caster,
            String mythicSkillId,
            Entity targetEntity,
            Location targetLocation,
            Map<String, String> variables) {
        if (targetEntity == null && targetLocation == null) {
            return castWithSelfMetadata(caster, mythicSkillId, variables);
        }
        Entity trigger = targetEntity == null ? caster : targetEntity;
        Location origin = targetLocation == null ? trigger.getLocation() : targetLocation;
        List<Entity> entityTargets = targetEntity == null ? List.of() : List.of(targetEntity);
        List<Location> locationTargets = targetLocation == null ? List.of() : List.of(targetLocation);
        return castSkillDetailed(caster, mythicSkillId, trigger, origin, entityTargets, locationTargets, variables);
    }

    public CastResult castWithSelfMetadata(Entity caster, String mythicSkillId, Map<String, String> variables) {
        if (caster == null) {
            return new CastResult(CastStatus.UNAVAILABLE, "");
        }
        Location origin = caster.getLocation();
        return castSkillDetailed(caster, mythicSkillId, caster, origin,
                List.of(caster), List.of(origin), variables);
    }

    private Consumer<SkillMetadata> metadataConsumer(Map<String, String> variables) {
        return metadata -> {
            if (metadata == null || variables == null || variables.isEmpty()) {
                return;
            }
            try {
                metadata.getParameters().putAll(variables);
            } catch (RuntimeException exception) {
                LogTags.send(plugin, LogTag.INTEGRATION, Level.WARNING, "integration.mythic_metadata_parameters_rejected", Map.of("parameters", variables.keySet(), "error", exception), exception);
            }
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                metadata.getVariables().putString(entry.getKey(), entry.getValue());
            }
        };
    }

    public void shutdown() {
        available = false;
        apiHelper = null;
    }

    private void info(String key) {
        if (messages != null) {
            messages.info(LogTag.INTEGRATION, key);
            return;
        }
        LogTags.sendRaw(plugin, LogTag.INTEGRATION, Level.INFO, key);
    }

    private void warning(String key, Map<String, ?> replacements, Throwable throwable) {
        String text = messages == null ? LogTags.head(plugin, "integration") + " " + key
                : MiniMessages.plainText(messages.message(key, replacements == null ? Map.of() : replacements));
        if (throwable == null) {
            plugin.getLogger().warning(text);
            return;
        }
        plugin.getLogger().log(Level.WARNING, text, throwable);
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
