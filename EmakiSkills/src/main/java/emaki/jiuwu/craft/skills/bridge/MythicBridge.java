package emaki.jiuwu.craft.skills.bridge;

import java.util.Map;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
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

    public boolean castSkill(Player caster, String mythicSkillId) {
        if (!available || apiHelper == null || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        try {
            return apiHelper.castSkill(caster, mythicSkillId);
        } catch (Exception exception) {
            warning("console.mythic_bridge_cast_failed", Map.of(
                    "skill", String.valueOf(mythicSkillId),
                    "error", errorMessage(exception)
            ), exception);
            return false;
        }
    }

    public boolean castSkill(Player caster, String mythicSkillId, Entity targetEntity, Location targetLocation) {
        if (!available || apiHelper == null || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        if (targetEntity == null && targetLocation == null) {
            return castSkill(caster, mythicSkillId);
        }
        try {
            Entity trigger = targetEntity == null ? caster : targetEntity;
            Location origin = targetLocation == null ? trigger.getLocation() : targetLocation;
            List<Entity> entityTargets = targetEntity == null ? List.of() : List.of(targetEntity);
            List<Location> locationTargets = targetLocation == null ? List.of() : List.of(targetLocation);
            return apiHelper.castSkill(caster, mythicSkillId, trigger, origin, entityTargets, locationTargets, 1.0F);
        } catch (Exception exception) {
            warning("console.mythic_bridge_cast_failed", Map.of(
                    "skill", String.valueOf(mythicSkillId),
                    "error", errorMessage(exception)
            ), exception);
            return false;
        }
    }

    public void shutdown() {
        available = false;
        apiHelper = null;
    }

    private void info(String key) {
        if (messages != null) {
            messages.info(key);
            return;
        }
        plugin.getLogger().info(key);
    }

    private void warning(String key, Map<String, ?> replacements, Throwable throwable) {
        String text = messages == null ? key : messages.message(key, replacements == null ? Map.of() : replacements);
        String plainText = messages == null ? text : MiniMessages.plain(messages.render(text));
        if (throwable == null) {
            plugin.getLogger().warning(plainText);
            return;
        }
        plugin.getLogger().log(Level.WARNING, plainText, throwable);
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
