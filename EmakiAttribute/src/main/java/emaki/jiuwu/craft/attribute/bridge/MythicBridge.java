package emaki.jiuwu.craft.attribute.bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeOutcome;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService.TemporaryAttributeMode;
import emaki.jiuwu.craft.attribute.service.TemporaryEffectSource;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.skills.variables.Variable;
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

    public void close() {
        attributeService.unregisterContributionProvider(mythicMobContributionProvider.id(),
                mythicMobContributionProvider);
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String name = normalize(event.getMechanicName());
        if (event.getConfig() == null) {
            return;
        }
        SkillExecutor executor = MythicBukkit.inst().getSkillManager();
        File sourceFile = new File(plugin.getDataFolder(), "mythic/" + name + ".yml");
        if (isDamageMechanic(name)) {
            event.register(new DamageSkillMechanic(executor, sourceFile, name, event.getConfig(), attributeService));
        } else if (isTemporaryAddMechanic(name)) {
            event.register(new TemporaryAttributeAddMechanic(executor, sourceFile, name, event.getConfig(), attributeService, plugin));
        } else if (isTemporaryRemoveMechanic(name)) {
            event.register(new TemporaryAttributeRemoveMechanic(executor, sourceFile, name, event.getConfig(), attributeService, plugin));
        } else if (isTemporaryAddTagMechanic(name)) {
            event.register(new TemporaryAttributeAddTagMechanic(executor, sourceFile, name, event.getConfig(), attributeService, plugin));
        } else if (isTemporaryClearTagMechanic(name)) {
            event.register(new TemporaryAttributeClearTagMechanic(executor, sourceFile, name, event.getConfig(), attributeService, plugin));
        }
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
        LivingEntity livingEntity = event.getEntity() instanceof LivingEntity candidate ? candidate : null;
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
            Entity bukkitEntity = activeMob.getEntity().getBukkitEntity();
            LivingEntity livingEntity = bukkitEntity instanceof LivingEntity livingEntityCandidate ? livingEntityCandidate : null;
            if (livingEntity != null) {
                attributeService.scheduleLivingEntitySync(livingEntity);
            }
        }
    }

    private boolean isDamageMechanic(String name) {
        return name.equals("emaki_damage") || name.equals("emakiattribute_damage") || name.equals("attribute_damage");
    }

    private boolean isTemporaryAddMechanic(String name) {
        return name.equals("emaki_attribute_add") || name.equals("emakiattribute_add");
    }

    private boolean isTemporaryRemoveMechanic(String name) {
        return name.equals("emaki_attribute_remove") || name.equals("emakiattribute_remove");
    }

    private boolean isTemporaryAddTagMechanic(String name) {
        return name.equals("emaki_attribute_add_tag") || name.equals("emakiattribute_add_tag");
    }

    private boolean isTemporaryClearTagMechanic(String name) {
        return name.equals("emaki_attribute_clear_tag") || name.equals("emakiattribute_clear_tag");
    }

    private boolean isAttributeCondition(String name) {
        return name.equals("emaki_attribute") || name.equals("emakiattribute_attribute") || name.equals("attribute_value") || name.equals("attribute_resource");
    }

    private String normalize(String value) {
        return Texts.normalizeId(value);
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
            plugin.getLogger().warning("Mythic mob attribute skipped: source=" + sourceId
                    + ", entry=" + entry + ", reason=missing_separator");
            return null;
        }
        String attributeName = entry.substring(0, separator).trim();
        String valueText = entry.substring(separator + 1).trim();
        if (Texts.isBlank(attributeName) || Texts.isBlank(valueText)) {
            plugin.getLogger().warning("Mythic mob attribute skipped: source=" + sourceId
                    + ", entry=" + entry + ", reason=blank_attribute_or_value");
            return null;
        }
        var definition = attributeService.attributeRegistry() == null ? null : attributeService.attributeRegistry().resolve(attributeName);
        if (definition == null) {
            plugin.getLogger().warning("Mythic mob attribute skipped: source=" + sourceId
                    + ", attribute=" + attributeName + ", reason=unregistered_attribute");
            return null;
        }
        Double value = parseMobAttributeValue(valueText);
        if (value == null) {
            plugin.getLogger().warning("Mythic mob attribute skipped: source=" + sourceId
                    + ", attribute=" + definition.id() + ", expression=" + valueText
                    + ", reason=unparsable_value");
            return null;
        }
        if (!Double.isFinite(value)) {
            plugin.getLogger().warning("Mythic mob attribute skipped: source=" + sourceId
                    + ", attribute=" + definition.id() + ", expression=" + valueText
                    + ", reason=non_finite_value");
            return null;
        }
        return new AttributeContribution(definition.id(), value, sourceId);
    }

    private Double parseMobAttributeValue(String valueText) {
        Double parsed = Numbers.tryParseDouble(valueText, null);
        if (parsed != null) {
            return parsed;
        }
        try {
            return ExpressionEngine.evaluate(valueText);
        } catch (Exception exception) {
            plugin.getLogger().warning("Mythic mob attribute expression failed: expression=" + valueText
                    + ", cause=" + exception);
            return null;
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
    public static final class DamageSkillMechanic extends SkillMechanic implements ITargetedEntitySkill {

        private static final String RESOLVED_DAMAGE_TYPE_VARIABLE = "emakiattribute_resolved_damage_type";

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
        public SkillResult castAtEntity(SkillMetadata metadata, AbstractEntity target) {
            LivingEntity livingTarget = resolveLiving(target);
            if (livingTarget == null) {
                return SkillResult.INVALID_TARGET;
            }
            LivingEntity attacker = resolveLiving(metadata.getCaster() == null ? null : metadata.getCaster().getEntity());
            if (attacker == null && metadata.getTrigger() != null) {
                attacker = resolveLiving(metadata.getTrigger());
            }
            double baseDamage = config.getDouble("damage", metadata.getPower());
            String damageTypeId = resolveDamageTypeId(metadata, attacker);
            boolean allowCritical = resolveBoolean(true, "allow_critical", "critical");
            boolean allowTargetDodge = resolveBoolean(false, "allow_target_dodge", "target_dodge", "allow_dodge", "dodge");
            boolean calculateTargetDefense = resolveBoolean(true, "calculate_target_defense", "target_defense", "calculate_defense", "defense");
            boolean triggerMythicOnDamaged = resolveBoolean(false, "trigger_mythic_on_damaged", "trigger_on_damaged", "mythic_on_damaged");
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
            context.put("allow_critical", allowCritical);
            context.put("allow_target_dodge", allowTargetDodge);
            context.put("calculate_target_defense", calculateTargetDefense);
            context.put("trigger_mythic_on_damaged", triggerMythicOnDamaged);
            boolean applied = attributeService.applyDamage(attacker, livingTarget, damageTypeId, baseDamage, context.build());
            return applied ? SkillResult.SUCCESS : SkillResult.CONDITION_FAILED;
        }

        private String resolveDamageTypeId(SkillMetadata metadata, LivingEntity attacker) {
            String configured = config.getString("damage_type", "");
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
            Variable cached = metadata.getVariables() == null ? null
                    : metadata.getVariables().get(RESOLVED_DAMAGE_TYPE_VARIABLE);
            String resolved = cached == null || cached.get() == null ? null : cached.get().toString();
            if (resolved == null || resolved.isBlank()) {
                resolved = attacker == null ? null : attributeService.consumeDamageTypeOverride(attacker);
                if (resolved == null || resolved.isBlank()) {
                    resolved = attributeService.defaultDamageTypeId();
                }
                if (metadata.getVariables() != null) {
                    metadata.getVariables().putString(RESOLVED_DAMAGE_TYPE_VARIABLE, resolved);
                }
            }
            return resolved == null || resolved.isBlank() ? attributeService.defaultDamageTypeId() : resolved;
        }

        private LivingEntity resolveLiving(AbstractEntity abstractEntity) {
            if (abstractEntity == null) {
                return null;
            }
            Entity entity = abstractEntity.getBukkitEntity();
            return entity instanceof LivingEntity livingEntity ? livingEntity : null;
        }

        private boolean resolveBoolean(boolean fallback, String... keys) {
            for (String key : keys) {
                String value = config.getString(key, null);
                if (value != null && !value.isBlank()) {
                    return Boolean.parseBoolean(value.trim());
                }
            }
            return fallback;
        }
    }

    private abstract static class AbstractTemporaryAttributeMechanic extends SkillMechanic implements ITargetedEntitySkill {

        protected final AttributeService attributeService;
        private final EmakiAttributePlugin plugin;

        protected AbstractTemporaryAttributeMechanic(SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config,
                AttributeService attributeService,
                EmakiAttributePlugin plugin) {
            super(executor, file, mechanicName, config);
            this.attributeService = attributeService;
            this.plugin = plugin;
        }

        protected LivingEntity resolveLiving(AbstractEntity abstractEntity) {
            if (abstractEntity == null) {
                return null;
            }
            Entity entity = abstractEntity.getBukkitEntity();
            return entity instanceof LivingEntity livingEntity ? livingEntity : null;
        }

        protected String configString(String fallback, String... keys) {
            for (String key : keys) {
                String value = config.getString(key, null);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return fallback;
        }

        protected String rawStackMode() {
            return configString("", "stack_mode", "stackmode", "mode");
        }

        protected boolean rejectsDeclaredArguments(String mechanic, double value) {
            String rawStackMode = rawStackMode();
            if (!rawStackMode.isBlank() && !TemporaryStackMode.isDeclared(rawStackMode)) {
                plugin.getLogger().warning("Mythic mechanic rejected at parse: mechanic=" + mechanic
                        + ", reason=unknown_stack_mode, stack_mode=" + rawStackMode);
                return true;
            }
            if (!Double.isFinite(value)) {
                plugin.getLogger().warning("Mythic mechanic rejected at parse: mechanic=" + mechanic
                        + ", reason=non_finite_value, value=" + value);
                return true;
            }
            return false;
        }

        protected SkillResult report(TemporaryAttributeOutcome outcome, String mechanic) {
            if (outcome.successful()) {
                return SkillResult.SUCCESS;
            }
            SkillResult result = switch (outcome.status()) {
                case NOT_FOUND, NO_MATCH -> SkillResult.CONDITION_FAILED;
                case INVALID_INPUT, UNKNOWN_ATTRIBUTE -> SkillResult.INVALID_CONFIG;
                case WRONG_THREAD, CLOSED -> SkillResult.ERROR;
                default -> SkillResult.ERROR;
            };
            if (outcome.status().rejected()) {
                plugin.getLogger().warning(
                        "Mythic mechanic refused: mechanic=" + mechanic
                                + ", status=" + outcome.status().name()
                                + ", group=" + outcome.groupId()
                                + ", attribute=" + outcome.attributeId()
                                + ", detail=" + outcome.detail());
            }
            return result;
        }
    }

    @MythicMechanic(
            name = "emaki_attribute_add",
            aliases = {"emakiattribute_add"},
            author = "Emaki",
            description = "Add a temporary attribute to the target through Emaki_Attribute.",
            version = "1.0.0",
            premium = false
    )
    public static final class TemporaryAttributeAddMechanic extends AbstractTemporaryAttributeMechanic {

        public TemporaryAttributeAddMechanic(SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config,
                AttributeService attributeService,
                EmakiAttributePlugin plugin) {
            super(executor, file, mechanicName, config, attributeService, plugin);
        }

        @Override
        public SkillResult castAtEntity(SkillMetadata metadata, AbstractEntity target) {
            LivingEntity livingTarget = resolveLiving(target);
            if (livingTarget == null) {
                return SkillResult.INVALID_TARGET;
            }
            String effectId = configString("", "effect_id", "effectid", "id");
            String attribute = configString("", "attribute", "attribute_id", "attributeid");
            if (effectId.isBlank() || attribute.isBlank()) {
                return SkillResult.INVALID_CONFIG;
            }
            double value = config.getDouble(new String[]{"value", "amount", "v"}, 0D);
            long durationTicks = config.getInteger(new String[]{"duration", "duration_ticks", "ticks", "d"}, 0);
            if (durationTicks <= 0L) {
                return SkillResult.INVALID_CONFIG;
            }
            if (rejectsDeclaredArguments("emaki_attribute_add", value)) {
                return SkillResult.INVALID_CONFIG;
            }
            boolean set = resolveBoolean(false, "set", "override_value");
            TemporaryAttributeMode mode = set ? TemporaryAttributeMode.SET : TemporaryAttributeMode.ADD;
            TemporaryAttributeOutcome outcome = attributeService.temporaryAttributeService().applyGroupEffect(
                    livingTarget, effectId, attribute, value, durationTicks, mode, rawStackMode(),
                    TemporaryEffectSource.MYTHIC);
            return report(outcome, "emaki_attribute_add");
        }

        private boolean resolveBoolean(boolean fallback, String... keys) {
            for (String key : keys) {
                String value = config.getString(key, null);
                if (value != null && !value.isBlank()) {
                    return Boolean.parseBoolean(value.trim());
                }
            }
            return fallback;
        }
    }

    @MythicMechanic(
            name = "emaki_attribute_remove",
            aliases = {"emakiattribute_remove"},
            author = "Emaki",
            description = "Remove a temporary attribute from the target through Emaki_Attribute.",
            version = "1.0.0",
            premium = false
    )
    public static final class TemporaryAttributeRemoveMechanic extends AbstractTemporaryAttributeMechanic {

        public TemporaryAttributeRemoveMechanic(SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config,
                AttributeService attributeService,
                EmakiAttributePlugin plugin) {
            super(executor, file, mechanicName, config, attributeService, plugin);
        }

        @Override
        public SkillResult castAtEntity(SkillMetadata metadata, AbstractEntity target) {
            LivingEntity livingTarget = resolveLiving(target);
            if (livingTarget == null) {
                return SkillResult.INVALID_TARGET;
            }
            String effectId = configString("", "effect_id", "effectid", "id");
            if (effectId.isBlank()) {
                return SkillResult.INVALID_CONFIG;
            }
            return report(attributeService.temporaryAttributeService().removeGroup(livingTarget, effectId),
                    "emaki_attribute_remove");
        }
    }

    @MythicMechanic(
            name = "emaki_attribute_add_tag",
            aliases = {"emakiattribute_add_tag"},
            author = "Emaki",
            description = "Add every temporary attribute carrying a tag to the target through Emaki_Attribute.",
            version = "1.0.0",
            premium = false
    )
    public static final class TemporaryAttributeAddTagMechanic extends AbstractTemporaryAttributeMechanic {

        public TemporaryAttributeAddTagMechanic(SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config,
                AttributeService attributeService,
                EmakiAttributePlugin plugin) {
            super(executor, file, mechanicName, config, attributeService, plugin);
        }

        @Override
        public SkillResult castAtEntity(SkillMetadata metadata, AbstractEntity target) {
            LivingEntity livingTarget = resolveLiving(target);
            if (livingTarget == null) {
                return SkillResult.INVALID_TARGET;
            }
            String tag = configString("", "tag", "tags", "t");
            if (tag.isBlank()) {
                return SkillResult.INVALID_CONFIG;
            }
            double value = config.getDouble(new String[]{"value", "amount", "v"}, 0D);
            long durationTicks = config.getInteger(new String[]{"duration", "duration_ticks", "ticks", "d"}, 0);
            if (durationTicks <= 0L) {
                return SkillResult.INVALID_CONFIG;
            }
            if (rejectsDeclaredArguments("emaki_attribute_add_tag", value)) {
                return SkillResult.INVALID_CONFIG;
            }
            String effectPrefix = configString("", "effect_prefix", "effectprefix", "prefix");
            TemporaryAttributeOutcome outcome = attributeService.temporaryAttributeService().addGroupByTag(
                    livingTarget, effectPrefix, tag, value, durationTicks, rawStackMode(),
                    TemporaryEffectSource.MYTHIC);
            return report(outcome, "emaki_attribute_add_tag");
        }
    }

    @MythicMechanic(
            name = "emaki_attribute_clear_tag",
            aliases = {"emakiattribute_clear_tag"},
            author = "Emaki",
            description = "Clear every temporary attribute carrying a tag from the target through Emaki_Attribute.",
            version = "1.0.0",
            premium = false
    )
    public static final class TemporaryAttributeClearTagMechanic extends AbstractTemporaryAttributeMechanic {

        public TemporaryAttributeClearTagMechanic(SkillExecutor executor,
                File file,
                String mechanicName,
                MythicLineConfig config,
                AttributeService attributeService,
                EmakiAttributePlugin plugin) {
            super(executor, file, mechanicName, config, attributeService, plugin);
        }

        @Override
        public SkillResult castAtEntity(SkillMetadata metadata, AbstractEntity target) {
            LivingEntity livingTarget = resolveLiving(target);
            if (livingTarget == null) {
                return SkillResult.INVALID_TARGET;
            }
            String tag = configString("", "tag", "tags", "t");
            if (tag.isBlank()) {
                return SkillResult.INVALID_CONFIG;
            }
            return report(attributeService.temporaryAttributeService().removeGroupByTag(livingTarget, tag),
                    "emaki_attribute_clear_tag");
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
            String sourceId = "mythic_mob:" + Texts.normalizeId(mythicMob.getInternalName());
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
            this.attributeId = Texts.normalizeId(config.getString("attribute", ""));
            this.resourceId = Texts.normalizeId(config.getString("resource", ""));
            this.field = Texts.normalizeId(config.getString("field", resourceId.isBlank() ? "value" : "current_value"));
            this.operator = Texts.normalizeId(config.getString("operator", ">="));
            this.value = config.getDouble("value", 0D);
            this.value2 = config.getDouble("value_2", value);
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
            Entity entity = abstractEntity.getBukkitEntity();
            return entity instanceof LivingEntity livingEntity ? livingEntity : null;
        }

    }
}
