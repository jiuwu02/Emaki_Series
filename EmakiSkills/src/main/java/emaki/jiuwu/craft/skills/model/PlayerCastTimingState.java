package emaki.jiuwu.craft.skills.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerCastTimingState {

    private long forcedGlobalCastDelayUntil;
    private long globalCooldownUntil;
    private final Map<String, Long> skillCooldownUntilBySkillId;

    public PlayerCastTimingState() {
        this.forcedGlobalCastDelayUntil = 0L;
        this.globalCooldownUntil = 0L;
        this.skillCooldownUntilBySkillId = new ConcurrentHashMap<>();
    }

    public long forcedGlobalCastDelayUntil() {
        return forcedGlobalCastDelayUntil;
    }

    public void setForcedGlobalCastDelayUntil(long forcedGlobalCastDelayUntil) {
        this.forcedGlobalCastDelayUntil = forcedGlobalCastDelayUntil;
    }

    public long globalCooldownUntil() {
        return globalCooldownUntil;
    }

    public void setGlobalCooldownUntil(long globalCooldownUntil) {
        this.globalCooldownUntil = globalCooldownUntil;
    }

    public Map<String, Long> skillCooldownUntilBySkillId() {
        return skillCooldownUntilBySkillId;
    }

    public boolean isForcedDelayActive() {
        return System.currentTimeMillis() < forcedGlobalCastDelayUntil;
    }

    public boolean isGlobalCooldownActive() {
        return System.currentTimeMillis() < globalCooldownUntil;
    }

    public boolean isSkillOnCooldown(String skillId) {
        Long until = skillCooldownUntilBySkillId.get(skillId);
        return until != null && System.currentTimeMillis() < until;
    }

    public void recordCast(String skillId, long cooldownTicks, long globalCooldownTicks, long forcedDelayTicks) {
        long now = System.currentTimeMillis();
        long tickMs = 50L;
        if (cooldownTicks > 0L) {
            skillCooldownUntilBySkillId.put(skillId, now + cooldownTicks * tickMs);
        }
        if (globalCooldownTicks > 0L) {
            globalCooldownUntil = now + globalCooldownTicks * tickMs;
        }
        if (forcedDelayTicks > 0L) {
            forcedGlobalCastDelayUntil = now + forcedDelayTicks * tickMs;
        }
    }

    public void clearAll() {
        forcedGlobalCastDelayUntil = 0L;
        globalCooldownUntil = 0L;
        skillCooldownUntilBySkillId.clear();
    }
}
