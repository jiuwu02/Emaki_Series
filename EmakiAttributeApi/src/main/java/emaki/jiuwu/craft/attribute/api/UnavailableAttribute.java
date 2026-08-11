package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.extension.AttributeExtensions;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.attribute.api.extension.ItemContributionGate;
import emaki.jiuwu.craft.attribute.api.extension.ItemContributionGateRegistration;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.attribute.api.model.ResourceDefinitionView;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/** Shared non-null layers used while EmakiAttribute is absent. */
final class UnavailableAttribute implements AttributeCatalog, AttributeOperations, AttributeExtensions, PdcAttributeAccess {

    private static final UnavailableAttribute INSTANCE = new UnavailableAttribute();

    static final AttributeCatalog CATALOG = INSTANCE;
    static final AttributeOperations OPERATIONS = INSTANCE;
    static final AttributeExtensions EXTENSIONS = INSTANCE;

    private UnavailableAttribute() {
    }

    @Override
    public EmakiResult<Double> attributeValue(Player player, String attributeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Double> resourceCurrent(Player player, String resourceId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Double> resourceMax(Player player, String resourceId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<AttributeSnapshot> itemSnapshot(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<AttributeSnapshot> combatSnapshot(LivingEntity entity) {
        return EmakiResult.unavailable();
    }

    @Override
    public Map<String, ResourceDefinitionView> resources() {
        return Map.of();
    }

    @Override
    public Set<String> attributeIds() {
        return Set.of();
    }

    @Override
    public Set<String> damageTypeIds() {
        return Set.of();
    }

    @Override
    public boolean isItemContributionActive(Player player, ItemStack itemStack, String slotName) {
        return true;
    }

    @Override
    public EmakiResult<Unit> consumeResource(Player player, String resourceId, double amount) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> scheduleEquipmentSync(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<DamageResult> calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> resyncPlayer(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public ContributionProviderRegistration registerContributionProvider(Plugin owner,
            AttributeContributionProvider provider) {
        return ContributionProviderRegistration.noop();
    }

    @Override
    public ItemContributionGateRegistration registerItemContributionGate(Plugin owner, ItemContributionGate gate) {
        return ItemContributionGateRegistration.noop();
    }

    @Override
    public PdcAttributeAccess pdc() {
        return this;
    }

    @Override
    public EmakiResult<Unit> registerSource(String sourceId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> unregisterSource(String sourceId) {
        return EmakiResult.unavailable();
    }

    @Override
    public boolean isRegisteredSource(String sourceId) {
        return false;
    }

    @Override
    public Set<String> registeredSources() {
        return Set.of();
    }

    @Override
    public EmakiResult<Unit> write(ItemStack itemStack, PdcAttributePayload payload) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<PdcAttributePayload> read(ItemStack itemStack, String sourceId) {
        return EmakiResult.unavailable();
    }

    @Override
    public Map<String, PdcAttributePayload> readAll(ItemStack itemStack) {
        return Map.of();
    }

    @Override
    public EmakiResult<Unit> clear(ItemStack itemStack, String sourceId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> clearAll(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> copy(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        return EmakiResult.unavailable();
    }
}
