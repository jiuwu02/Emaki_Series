package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

abstract class AbstractAttributeServiceFacade implements AttributeServiceFacade {

    protected abstract AttributeConfig configRef();

    protected abstract void updateConfig(AttributeConfig config);

    protected abstract AttributeRegistry attributeRegistryRef();

    protected abstract AttributeBalanceRegistry attributeBalanceRegistryRef();

    protected abstract DamageTypeRegistry damageTypeRegistryRef();

    protected abstract DefaultProfileRegistry defaultProfileRegistryRef();

    protected abstract LoreFormatRegistry loreFormatRegistryRef();

    protected abstract AttributePresetRegistry presetRegistryRef();

    protected abstract LoreParser loreParserRef();

    protected abstract DamageEngine damageEngineRef();

    protected abstract EmakiAttributePlugin pluginRef();

    protected abstract AttributeRegistryService registryServiceRef();

    protected abstract AttributeSnapshotService snapshotServiceRef();

    protected abstract ResourceManagementService resourceManagementServiceRef();

    protected abstract DamageCalculationService damageCalculationServiceRef();

    @Override
    public AttributeConfig config() {
        return configRef();
    }

    @Override
    public AttributeRegistry attributeRegistry() {
        return attributeRegistryRef();
    }

    @Override
    public AttributeBalanceRegistry attributeBalanceRegistry() {
        return attributeBalanceRegistryRef();
    }

    @Override
    public DamageTypeRegistry damageTypeRegistry() {
        return damageTypeRegistryRef();
    }

    @Override
    public DefaultProfileRegistry defaultProfileRegistry() {
        return defaultProfileRegistryRef();
    }

    @Override
    public LoreFormatRegistry loreFormatRegistry() {
        return loreFormatRegistryRef();
    }

    @Override
    public AttributePresetRegistry presetRegistry() {
        return presetRegistryRef();
    }

    @Override
    public LoreParser loreParser() {
        return loreParserRef();
    }

    @Override
    public DamageEngine damageEngine() {
        return damageEngineRef();
    }

    @Override
    public EmakiAttributePlugin plugin() {
        return pluginRef();
    }

    @Override
    public void reloadConfig(AttributeConfig config) {
        updateConfig(config == null ? AttributeConfig.defaults() : config);
    }

    @Override
    public void refreshCaches() {
        registryServiceRef().refreshCaches();
    }

    @Override
    public String defaultDamageTypeId() {
        return damageCalculationServiceRef().defaultDamageTypeId();
    }

    @Override
    public String defaultProjectileDamageTypeId() {
        return damageCalculationServiceRef().defaultProjectileDamageTypeId();
    }

    @Override
    public DamageTypeDefinition resolveDamageType(String damageTypeId) {
        return damageCalculationServiceRef().resolveDamageType(damageTypeId);
    }

    @Override
    public String defaultProfileSignature() {
        return registryServiceRef().combatBaseSignature();
    }

    @Override
    public Map<String, ResourceDefinition> resourceDefinitions() {
        return registryServiceRef().resourceDefinitions();
    }

    @Override
    public Map<String, Double> defaultAttributeValues() {
        return registryServiceRef().defaultAttributeValues();
    }

    @Override
    public Double resolveAttributeValue(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || Texts.isBlank(attributeId)) {
            return null;
        }
        AttributeDefinition definition = attributeRegistryRef().resolve(attributeId);
        String normalized = normalizeId(attributeId);
        Double value = definition == null ? null : snapshot.values().get(definition.id());
        if (value != null) {
            return value;
        }
        value = snapshot.values().get(normalized);
        return value != null || definition == null ? value : snapshot.values().get(definition.id());
    }

    @Override
    public void registerContributionProvider(AttributeContributionProvider provider) {
        registryServiceRef().registerContributionProvider(provider);
    }

    @Override
    public void unregisterContributionProvider(String providerId) {
        registryServiceRef().unregisterContributionProvider(providerId);
    }

    @Override
    public AttributeSnapshot collectItemSnapshot(ItemStack itemStack) {
        return snapshotServiceRef().collectItemSnapshot(itemStack);
    }

    @Override
    public AttributeSnapshot collectCombatSnapshot(LivingEntity entity) {
        return snapshotServiceRef().collectCombatSnapshot(entity);
    }

    @Override
    public AttributeSnapshot collectPlayerCombatSnapshot(Player player) {
        return snapshotServiceRef().collectPlayerCombatSnapshot(player);
    }

    @Override
    public void resyncAllPlayers() {
        resourceManagementServiceRef().resyncAllPlayers();
    }

    @Override
    public void regenerateOnlinePlayers() {
        resourceManagementServiceRef().regenerateOnlinePlayers();
    }

    @Override
    public void resyncPlayer(Player player) {
        resourceManagementServiceRef().resyncPlayer(player);
    }

    @Override
    public void scheduleJoinHealthSync(Player player) {
        resourceManagementServiceRef().scheduleJoinHealthSync(player);
    }

    @Override
    public void scheduleRespawnHealthSync(Player player) {
        resourceManagementServiceRef().scheduleRespawnHealthSync(player);
    }

    @Override
    public void scheduleHealthSync(LivingEntity entity) {
        resourceManagementServiceRef().scheduleHealthSync(entity);
    }

    @Override
    public void scheduleEquipmentSync(Player player) {
        resourceManagementServiceRef().scheduleEquipmentSync(player);
    }

    @Override
    public void scheduleLivingEntitySync(LivingEntity entity) {
        resourceManagementServiceRef().scheduleLivingEntitySync(entity);
    }

    @Override
    public void syncLivingEntity(LivingEntity entity) {
        resourceManagementServiceRef().syncLivingEntity(entity);
    }

    @Override
    public void syncPlayer(Player player, ResourceSyncReason reason, Double healthOverride) {
        resourceManagementServiceRef().syncPlayer(player, reason, healthOverride);
    }

    @Override
    public ResourceState syncResource(Player player,
                                      ResourceDefinition resourceDefinition,
                                      AttributeSnapshot snapshot,
                                      ResourceSyncReason reason,
                                      Double currentValueOverride) {
        return resourceManagementServiceRef().syncResource(player, resourceDefinition, snapshot, reason, currentValueOverride);
    }

    @Override
    public ResourceState readResourceState(Player player, String resourceId) {
        return resourceManagementServiceRef().readResourceState(player, resourceId);
    }

    @Override
    public void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        damageCalculationServiceRef().setDamageTypeOverride(entity, damageTypeId);
    }

    @Override
    public String peekDamageTypeOverride(LivingEntity entity) {
        return damageCalculationServiceRef().peekDamageTypeOverride(entity);
    }

    @Override
    public String consumeDamageTypeOverride(LivingEntity entity) {
        return damageCalculationServiceRef().consumeDamageTypeOverride(entity);
    }

    @Override
    public void markSyntheticDamage(LivingEntity entity, boolean value) {
        damageCalculationServiceRef().markSyntheticDamage(entity, value);
    }

    @Override
    public boolean isSyntheticDamage(LivingEntity entity) {
        return damageCalculationServiceRef().isSyntheticDamage(entity);
    }

    @Override
    public ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) {
        return damageCalculationServiceRef().snapshotProjectile(projectile, shooter);
    }

    @Override
    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        return damageCalculationServiceRef().readProjectileSnapshot(projectile);
    }

    @Override
    public DamageContext createDamageContext(LivingEntity attacker,
                                             LivingEntity target,
                                             Projectile projectile,
                                             EntityDamageEvent.DamageCause cause,
                                             String damageTypeId,
                                             double sourceDamage,
                                             double baseDamage,
                                             AttributeSnapshot attackerSnapshot,
                                             AttributeSnapshot targetSnapshot,
                                             DamageContextVariables context) {
        return damageCalculationServiceRef().createDamageContext(
            attacker,
            target,
            projectile,
            cause,
            damageTypeId,
            sourceDamage,
            baseDamage,
            attackerSnapshot,
            targetSnapshot,
            context
        );
    }

    @Override
    public DamageResult calculateDamage(DamageContext damageContext) {
        return damageCalculationServiceRef().calculateDamage(damageContext);
    }

    public ResolvedDamage resolveDamageApplication(DamageContext damageContext) {
        return damageCalculationServiceRef().resolveDamageApplication(damageContext);
    }

    @Override
    public boolean applyDamage(DamageContext damageContext) {
        return damageCalculationServiceRef().applyDamage(damageContext);
    }

    public boolean applyResolvedDamage(ResolvedDamage resolvedDamage, Entity visualSource, double alreadyAppliedDamage) {
        return damageCalculationServiceRef().applyResolvedDamage(resolvedDamage, visualSource, alreadyAppliedDamage);
    }

    @Override
    public boolean applyProjectileDamage(DamageContext damageContext) {
        return damageCalculationServiceRef().applyProjectileDamage(damageContext);
    }

    @Override
    public void clearPlayerDamageTypeOverride(Player player) {
        damageCalculationServiceRef().clearPlayerDamageTypeOverride(player);
    }

    @Override
    public boolean isAttackCoolingDown(Player player) {
        return resourceManagementServiceRef().isAttackCoolingDown(player);
    }

    @Override
    public int startAttackCooldown(Player player, AttributeSnapshot snapshot, ItemStack itemStack) {
        return resourceManagementServiceRef().startAttackCooldown(player, snapshot, itemStack);
    }

    protected String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }
}
