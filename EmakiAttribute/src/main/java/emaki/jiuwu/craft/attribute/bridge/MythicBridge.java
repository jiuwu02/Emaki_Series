package emaki.jiuwu.craft.attribute.bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;

public final class MythicBridge implements Listener {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final AttributeContributionProvider mythicMobContributionProvider;

    public MythicBridge(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.mythicMobContributionProvider = new MythicMobAttributeContributionProvider();
        this.attributeService.registerContributionProvider(mythicMobContributionProvider);
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String name = normalize(event.getMechanicName());
        if (!isDamageMechanic(name) || event.getConfig() == null) {
            return;
        }
        SkillExecutor executor = MythicBukkit.inst().getSkillManager();
        File sourceFile = new File(plugin.getDataFolder(), "mythic/" + name + ".yml");
        event.register(new DamageSkillMechanic(executor, sourceFile, name, event.getConfig(), attributeService));
    }

    @EventHandler
    public void onConditionLoad(MythicConditionLoadEvent event) {
        String name = normalize(event.getConditionName());
        if (!isAttributeCondition(name) || event.getConfig() == null) {
            return;
        }
        event.register(new AttributeCondition(name, event.getArgument(), event.getConfig(), attributeService));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobSpawn(MythicMobSpawnEvent event) {
        LivingEntity livingEntity = event.getLivingEntity();
        if (livingEntity == null) {
            return;
        }
        attributeService.syncLivingEntity(livingEntity);
        attributeService.scheduleLivingEntitySync(livingEntity);
    }

    @EventHandler
    public void onPostReloaded(MythicPostReloadedEvent event) {
        resyncActiveMobs();
    }

    public void resyncActiveMobs() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        Collection<ActiveMob> activeMobs = MythicBukkit.inst().getMobManager().getActiveMobs();
        if (activeMobs == null || activeMobs.isEmpty()) {
            return;
        }
        for (ActiveMob activeMob : activeMobs) {
            if (activeMob == null || activeMob.getEntity() == null) {
                continue;
            }
            org.bukkit.entity.Entity bukkitEntity = activeMob.getEntity().getBukkitEntity();
            LivingEntity livingEntity = bukkitEntity instanceof LivingEntity livingEntityCandidate ? livingEntityCandidate : null;
            if (livingEntity != null) {
                attributeService.scheduleLivingEntitySync(livingEntity);
            }
        }
    }

    private boolean isDamageMechanic(String name) {
        return name.equals("emaki_damage") || name.equals("emakiattribute_damage") || name.equals("attribute_damage");
    }

    private boolean isAttributeCondition(String name) {
        return name.equals("emaki_attribute") || name.equals("emakiattribute_attribute") || name.equals("attribute_value") || name.equals("attribute_resource");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private AttributeContribution parseMobAttributeEntry(String rawEntry, String sourceId) {
        String entry = Texts.toStringSafe(rawEntry).trim();
        if (Texts.isBlank(entry)) {
            return null;
        }
        int separator = entry.indexOf(':');
        if (separator < 0) {
            separator = entry.indexOf('：');
        }
        if (separator < 0) {
            return null;
        }
        String attributeName = entry.substring(0, separator).trim();
        String valueText = entry.substring(separator + 1).trim();
        if (Texts.isBlank(attributeName) || Texts.isBlank(valueText)) {
            return null;
        }
        var definition = attributeService.attributeRegistry() == null ? null : attributeService.attributeRegistry().resolve(attributeName);
        String attributeId = definition == null ? normalizeId(attributeName) : definition.id();
        double value = parseMobAttributeValue(valueText);
        return new AttributeContribution(attributeId, value, sourceId);
    }

    private double parseMobAttributeValue(String valueText) {
        Double parsed = Numbers.tryParseDouble(valueText, null);
        if (parsed != null) {
            return parsed;
        }
        try {
            return ExpressionEngine.evaluate(valueText);
        } catch (Exception ignored) {
            return 0D;
        }
    }

    @MythicMechanic(
            name = "emaki_damage",
            aliases = {"emakiattribute_damage", "attribute_damage"},
            author = "Emaki",
            description = "Deal attribute-based damage through Emaki_Attribute.",
            version = "1.0.0",
            premium = false
    )
    public static final class DamageSkillMechanic extends SkillMechanic {

        private final AttributeService attributeService;

        public DamageSkillMechanic(SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config,
                AttributeService attributeService) {
            super(executor, file, mechanicName, config);
            this.attributeService = attributeService;
        }

        @Override
        public boolean executeSkills(SkillMetadata metadata) {
            LivingEntity attacker = resolveLiving(metadata.getCaster() == null ? null : metadata.getCaster().getEntity());
            if (attacker == null && metadata.getTrigger() != null) {
                attacker = resolveLiving(metadata.getTrigger());
            }
            double baseDamage = config.getDouble("damage", config.getDouble("base", metadata.getPower()));
            String damageTypeId = config.getString("damage_type", config.getString("type", ""));
            if (damageTypeId.isBlank() && attacker != null) {
                damageTypeId = attributeService.consumeDamageTypeOverride(attacker);
            }
            if (damageTypeId == null || damageTypeId.isBlank()) {
                damageTypeId = attributeService.defaultDamageTypeId();
            }
            DamageContextVariables.Builder context = DamageContextVariables.builder();
            context.put("mythic_skill", getTypeName());
            context.put("mythic_power", metadata.getPower());
            context.put("mythic_trigger", metadata.getTrigger() == null ? "" : metadata.getTrigger().getUniqueId().toString());
            context.put("mythic_cause", metadata.getCause() == null ? "" : metadata.getCause().name());
            context.put("damage_type", damageTypeId);
            Map<String, String> parameters = metadata.getParameters();
            if (parameters != null) {
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                    context.put(key, entry.getValue());
                }
            }
            boolean applied = false;
            Collection<AbstractEntity> targets = metadata.getEntityTargets();
            if (targets != null && !targets.isEmpty()) {
                for (AbstractEntity abstractTarget : targets) {
                    LivingEntity target = resolveLiving(abstractTarget);
                    if (target == null) {
                        continue;
                    }
                    applied |= attributeService.applyDamage(attacker, target, damageTypeId, baseDamage, context.build());
                }
                return applied;
            }
            LivingEntity trigger = resolveLiving(metadata.getTrigger());
            if (trigger != null && trigger != attacker) {
                applied = attributeService.applyDamage(attacker, trigger, damageTypeId, baseDamage, context.build());
            }
            return applied;
        }

        private LivingEntity resolveLiving(AbstractEntity abstractEntity) {
            if (abstractEntity == null) {
                return null;
            }
            org.bukkit.entity.Entity entity = abstractEntity.getBukkitEntity();
            return entity instanceof LivingEntity livingEntity ? livingEntity : null;
        }
    }

    private final class MythicMobAttributeContributionProvider implements AttributeContributionProvider {

        @Override
        public String id() {
            return "mythic_mob_attributes";
        }

        @Override
        public int priority() {
            return 250;
        }

        @Override
        public Collection<AttributeContribution> collect(LivingEntity entity) {
            if (entity == null || entity instanceof Player || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                return List.of();
            }
            SkillCaster caster;
            try {
                caster = MythicBukkit.inst().getMobManager().getSkillCaster(entity.getUniqueId()).orElse(null);
            } catch (Exception exception) {
                return List.of();
            }
            if (!(caster instanceof ActiveMob activeMob)) {
                return List.of();
            }
            MythicMob mythicMob = activeMob.getType();
            if (mythicMob == null || mythicMob.getConfig() == null) {
                return List.of();
            }
            List<String> entries = mythicMob.getConfig().getStringList("EmakiAttribute");
            if (entries == null || entries.isEmpty()) {
                return List.of();
            }
            String sourceId = "mythic_mob:" + normalizeId(mythicMob.getInternalName());
            List<AttributeContribution> contributions = new ArrayList<>();
            for (String rawEntry : entries) {
                AttributeContribution contribution = parseMobAttributeEntry(rawEntry, sourceId);
                if (contribution != null) {
                    contributions.add(contribution);
                }
            }
            return contributions.isEmpty() ? List.of() : contributions;
        }
    }

    @MythicCondition(
            name = "emaki_attribute",
            aliases = {"emakiattribute_attribute", "attribute_value", "attribute_resource"},
            author = "Emaki",
            description = "Check an Emaki_Attribute snapshot or resource value.",
            version = "1.0.0",
            premium = false
    )
    public static final class AttributeCondition extends SkillCondition {

        private final AttributeService attributeService;
        private final String attributeId;
        private final String resourceId;
        private final String field;
        private final String operator;
        private final double value;
        private final double value2;

        public AttributeCondition(String conditionName,
                String argument,
                MythicLineConfig config,
                AttributeService attributeService) {
            super(conditionName);
            this.attributeService = attributeService;
            this.attributeId = normalizeId(config.getString("attribute", config.getString("id", argument)));
            this.resourceId = normalizeId(config.getString("resource", ""));
            this.field = normalizeId(config.getString("field", resourceId.isBlank() ? "value" : "current_value"));
            this.operator = normalizeId(config.getString("operator", config.getString("compare", ">=")));
            this.value = config.getDouble("value", config.getDouble("min", 0D));
            this.value2 = config.getDouble("value_2", config.getDouble("max", value));
        }

        @Override
        public boolean evaluateEntity(AbstractEntity entity) {
            return evaluate(resolveLiving(entity));
        }

        @Override
        public boolean evaluateCaster(SkillMetadata metadata) {
            return evaluate(resolveLiving(metadata.getCaster() == null ? null : metadata.getCaster().getEntity()));
        }

        @Override
        public boolean evaluateTrigger(SkillMetadata metadata) {
            return evaluate(resolveLiving(metadata.getTrigger()));
        }

        @Override
        public boolean evaluateToEntity(SkillMetadata metadata, AbstractEntity target) {
            return evaluate(resolveLiving(target));
        }

        @Override
        public boolean evaluateToEntity(AbstractEntity source, AbstractEntity target) {
            return evaluate(resolveLiving(target));
        }

        @Override
        public boolean evaluateTargets(SkillMetadata metadata) {
            Collection<AbstractEntity> targets = metadata.getEntityTargets();
            if (targets == null || targets.isEmpty()) {
                return false;
            }
            for (AbstractEntity abstractEntity : targets) {
                if (!evaluate(resolveLiving(abstractEntity))) {
                    return false;
                }
            }
            return true;
        }

        private boolean evaluate(LivingEntity livingEntity) {
            if (livingEntity == null || (attributeId.isBlank() && resourceId.isBlank())) {
                return false;
            }
            double currentValue = readCurrentValue(livingEntity);
            return switch (operator) {
                case ">", "gt" ->
                    currentValue > value;
                case ">=", "gte" ->
                    currentValue >= value;
                case "<", "lt" ->
                    currentValue < value;
                case "<=", "lte" ->
                    currentValue <= value;
                case "!=", "<>", "ne" ->
                    currentValue != value;
                case "between" ->
                    currentValue >= Math.min(value, value2) && currentValue <= Math.max(value, value2);
                default ->
                    currentValue >= value;
            };
        }

        private double readCurrentValue(LivingEntity livingEntity) {
            if (!resourceId.isBlank() && livingEntity instanceof Player player) {
                ResourceState state = attributeService.readResourceState(player, resourceId);
                if (state == null) {
                    return 0D;
                }
                return switch (field) {
                    case "default", "default_max" ->
                        state.defaultMax();
                    case "bonus", "bonus_max" ->
                        state.bonusMax();
                    case "max", "current_max" ->
                        state.currentMax();
                    case "percent" ->
                        state.currentMax() <= 0D ? 0D : (state.currentValue() / state.currentMax()) * 100D;
                    case "current", "current_value", "value" ->
                        state.currentValue();
                    default ->
                        state.currentValue();
                };
            }
            AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(livingEntity);
            Double value = attributeService.resolveAttributeValue(snapshot, attributeId);
            return value == null ? 0D : value;
        }

        private LivingEntity resolveLiving(AbstractEntity abstractEntity) {
            if (abstractEntity == null) {
                return null;
            }
            org.bukkit.entity.Entity entity = abstractEntity.getBukkitEntity();
            return entity instanceof LivingEntity livingEntity ? livingEntity : null;
        }

        private String normalizeId(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        }
    }
}
