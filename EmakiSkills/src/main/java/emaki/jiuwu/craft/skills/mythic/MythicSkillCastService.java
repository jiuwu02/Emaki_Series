package emaki.jiuwu.craft.skills.mythic;

import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.bridge.MythicBridge;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;

public final class MythicSkillCastService {

    private final MythicBridge mythicBridge;

    public MythicSkillCastService(MythicBridge mythicBridge) {
        this.mythicBridge = mythicBridge;
    }

    public boolean isAvailable() {
        return mythicBridge != null && mythicBridge.isAvailable();
    }

    public boolean skillExists(String mythicSkillId) {
        if (!isAvailable() || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        return mythicBridge.skillExists(mythicSkillId);
    }

    public MythicBridge.CastResult cast(Player caster, String mythicSkillId) {
        return cast(caster, mythicSkillId, null, Map.of());
    }

    public MythicBridge.CastResult castFromEntity(Entity caster, String mythicSkillId) {
        if (!isAvailable() || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return new MythicBridge.CastResult(MythicBridge.CastStatus.UNAVAILABLE, "");
        }
        return mythicBridge.castWithSelfMetadata(caster, mythicSkillId, Map.of());
    }

    public MythicBridge.CastResult cast(Player caster, String mythicSkillId, TriggerInvocation invocation) {
        return cast(caster, mythicSkillId, invocation, Map.of());
    }

    public MythicBridge.CastResult cast(Player caster,
            String mythicSkillId,
            TriggerInvocation invocation,
            Map<String, String> variables) {
        if (!isAvailable() || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return new MythicBridge.CastResult(MythicBridge.CastStatus.UNAVAILABLE, "");
        }
        return mythicBridge.castSkillForInvocation(caster, mythicSkillId,
                invocation == null ? null : invocation.targetEntity(),
                invocation == null ? null : invocation.targetLocation(),
                variables == null ? Map.of() : variables);
    }
}
