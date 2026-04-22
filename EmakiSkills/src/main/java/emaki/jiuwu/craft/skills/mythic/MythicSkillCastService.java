package emaki.jiuwu.craft.skills.mythic;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.bridge.MythicBridge;

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
}
