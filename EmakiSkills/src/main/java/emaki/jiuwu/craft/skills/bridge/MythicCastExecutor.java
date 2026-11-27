package emaki.jiuwu.craft.skills.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.mobs.GenericCaster;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.skills.AbstractSkill;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import io.lumine.mythic.core.skills.SkillTriggers;

final class MythicCastExecutor {

    private MythicCastExecutor() {
    }

    static MythicBridge.CastResult cast(BukkitAPIHelper apiHelper,
            Entity caster,
            String mythicSkillId,
            Entity trigger,
            Location origin,
            List<Entity> entityTargets,
            List<Location> locationTargets,
            Map<String, String> variables) {
        Optional<Skill> skill = MythicBukkit.inst().getSkillManager().getSkill(mythicSkillId);
        if (skill.isEmpty()) {
            return new MythicBridge.CastResult(MythicBridge.CastStatus.SKILL_NOT_FOUND, mythicSkillId);
        }
        Entity triggerEntity = trigger == null ? caster : trigger;
        Location originLocation = origin == null ? triggerEntity.getLocation() : origin;
        SkillMetadata metadata = buildMetadata(apiHelper, caster, triggerEntity, originLocation, entityTargets, locationTargets);
        if (variables != null && !variables.isEmpty()) {
            metadata.getParameters().putAll(variables);
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                metadata.getVariables().putString(entry.getKey(), entry.getValue());
            }
        }
        boolean executed = skill.get().executeIfUsable(metadata, SkillTriggers.API);
        if (executed) {
            return new MythicBridge.CastResult(MythicBridge.CastStatus.SUCCESS, "");
        }
        return new MythicBridge.CastResult(MythicBridge.CastStatus.CONDITIONS_UNMET,
                conditionSummary(skill.get(), metadata));
    }

    private static SkillMetadata buildMetadata(BukkitAPIHelper apiHelper,
            Entity caster,
            Entity trigger,
            Location origin,
            List<Entity> entityTargets,
            List<Location> locationTargets) {
        List<AbstractEntity> adaptedEntities = new ArrayList<>();
        if (entityTargets != null) {
            for (Entity entity : entityTargets) {
                if (entity != null) {
                    adaptedEntities.add(BukkitAdapter.adapt(entity));
                }
            }
        }
        List<AbstractLocation> adaptedLocations = new ArrayList<>();
        if (locationTargets != null) {
            for (Location location : locationTargets) {
                if (location != null) {
                    adaptedLocations.add(BukkitAdapter.adapt(location));
                }
            }
        }
        return new SkillMetadataImpl(SkillTriggers.API, resolveCaster(apiHelper, caster),
                BukkitAdapter.adapt(trigger), BukkitAdapter.adapt(origin),
                adaptedEntities, adaptedLocations, 1.0F);
    }

    private static SkillCaster resolveCaster(BukkitAPIHelper apiHelper, Entity caster) {
        ActiveMob activeMob = apiHelper.getMythicMobInstance(caster);
        if (activeMob != null) {
            return activeMob;
        }
        if (caster instanceof Player) {
            Object profile = MythicBukkit.inst().getPlayerManager().getProfile(caster.getUniqueId()).orElse(null);
            if (profile instanceof SkillCaster skillCaster) {
                return skillCaster;
            }
        }
        return new GenericCaster(BukkitAdapter.adapt(caster));
    }

    private static String conditionSummary(Skill skill, SkillMetadata metadata) {
        if (!(skill instanceof AbstractSkill abstractSkill)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendConditions(builder, "Conditions", abstractSkill.getConditions());
        appendConditions(builder, "TargetConditions", abstractSkill.getConditionsTarget());
        appendConditions(builder, "TriggerConditions", abstractSkill.getConditionsTrigger());
        builder.append("; Targets=").append(describeTargets(metadata));
        return builder.toString();
    }

    private static String describeTargets(SkillMetadata metadata) {
        if (metadata == null) {
            return "none";
        }
        List<String> labels = new ArrayList<>();
        if (metadata.getEntityTargets() != null) {
            for (AbstractEntity target : metadata.getEntityTargets()) {
                if (target != null) {
                    labels.add(String.valueOf(target.getBukkitEntity().getType()) + "@"
                            + shorten(target.getUniqueId() == null ? "" : target.getUniqueId().toString()));
                }
            }
        }
        if (labels.isEmpty()) {
            return "none";
        }
        return String.join(",", labels);
    }

    private static String shorten(String value) {
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static void appendConditions(StringBuilder builder, String group, List<SkillCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("; ");
        }
        builder.append(group).append('=');
        List<String> names = new ArrayList<>();
        for (SkillCondition condition : conditions) {
            names.add(condition.getTypeName());
        }
        builder.append(names);
    }
}
