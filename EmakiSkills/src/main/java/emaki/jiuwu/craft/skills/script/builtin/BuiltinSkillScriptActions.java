package emaki.jiuwu.craft.skills.script.builtin;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;

public final class BuiltinSkillScriptActions {

    private BuiltinSkillScriptActions() {
    }

    public static void registerAll(SkillScriptActionRegistry registry,
            Plugin owner,
            MythicSkillCastService mythicSkillCastService) {
        registry.register(owner, new MessageSkillAction());
        registry.register(owner, new SoundSkillAction());
        registry.register(owner, new ParticleSkillAction());
        registry.register(owner, new RaySkillAction());
        registry.register(owner, new DamageSkillAction());
        registry.register(owner, new AoeDamageSkillAction());
        registry.register(owner, new ProjectileSkillAction());
        registry.register(owner, new IgniteSkillAction());
        registry.register(owner, new HealSkillAction());
        registry.register(owner, new MythicSkillAction(mythicSkillCastService));
    }
}
