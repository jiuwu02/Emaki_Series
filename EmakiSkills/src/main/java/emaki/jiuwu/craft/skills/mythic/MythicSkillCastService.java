package emaki.jiuwu.craft.skills.mythic;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.bridge.MythicBridge;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

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

    public boolean cast(Player caster, String mythicSkillId) {
        if (!isAvailable() || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        return mythicBridge.castSkill(caster, mythicSkillId);
    }

    /**
     * Casts with any entity as the caster.
     *
     * <p>Kept separate from {@link #cast(Player, String)} so the player flow keeps its typed entry point: a
     * non-player caster has no skill profile, so it cannot go through cooldown or resource handling.</p>
     *
     * @param caster the casting entity
     * @param mythicSkillId the Mythic skill id
     * @return whether MythicMobs accepted the cast
     */
    public boolean castFromEntity(Entity caster, String mythicSkillId) {
        if (!isAvailable() || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        return mythicBridge.castSkillFromEntity(caster, mythicSkillId);
    }

    public boolean cast(Player caster, String mythicSkillId, TriggerInvocation invocation) {
        return cast(caster, mythicSkillId, invocation, ResolvedSkillParameters.empty());
    }

    public boolean cast(Player caster,
            String mythicSkillId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        if (!isAvailable() || caster == null || mythicSkillId == null || mythicSkillId.isBlank()) {
            return false;
        }
        if (invocation == null) {
            return mythicBridge.castSkill(caster, mythicSkillId, parameters);
        }
        return mythicBridge.castSkill(caster, mythicSkillId,
                invocation.targetEntity(), invocation.targetLocation(), parameters);
    }
}
