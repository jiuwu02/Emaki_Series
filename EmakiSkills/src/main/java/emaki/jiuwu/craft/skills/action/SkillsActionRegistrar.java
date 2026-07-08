package emaki.jiuwu.craft.skills.action;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

public final class SkillsActionRegistrar {

    public static final String SOURCE = "emakiskills";

    private SkillsActionRegistrar() {
    }

    public static void registerAll(ActionRegistry registry,
            Plugin owner,
            MythicSkillCastService mythicSkillCastService,
            PlayerSkillStateService stateService,
            SkillLevelService levelService,
            SkillUpgradeService upgradeService,
            PlayerSkillDataStore dataStore) {
        if (registry == null || owner == null) {
            return;
        }
        unregisterAll(registry, owner);
        registry.register(owner, SOURCE, new CastSkillAction(mythicSkillCastService));
        registry.register(owner, SOURCE, new SkillLevelAction(SkillLevelAction.SET_LEVEL_ID, stateService, levelService, upgradeService, dataStore));
        registry.register(owner, SOURCE, new SkillLevelAction(SkillLevelAction.UPGRADE_ID, stateService, levelService, upgradeService, dataStore));
        registry.register(owner, SOURCE, new SkillSlotAction(SkillSlotAction.EQUIP_ID, stateService, dataStore));
        registry.register(owner, SOURCE, new SkillSlotAction(SkillSlotAction.UNEQUIP_ID, stateService, dataStore));
        registry.register(owner, SOURCE, new SkillSlotAction(SkillSlotAction.BIND_ID, stateService, dataStore));
        registry.register(owner, SOURCE, new SkillCooldownAction(SkillCooldownAction.CLEAR_ID, stateService, dataStore));
        registry.register(owner, SOURCE, new SkillCooldownAction(SkillCooldownAction.SET_ID, stateService, dataStore));
    }

    public static void unregisterAll(ActionRegistry registry, Plugin owner) {
        if (registry == null || owner == null) {
            return;
        }
        registry.unregisterAll(owner);
    }
}
