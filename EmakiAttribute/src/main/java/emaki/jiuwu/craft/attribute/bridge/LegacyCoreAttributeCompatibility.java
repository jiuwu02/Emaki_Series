package emaki.jiuwu.craft.attribute.bridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.api.integration.PdcAttributePayloadSnapshot;

/**
 * Single compatibility adapter exposing the deprecated CoreLib Attribute
 * mirrors on top of the canonical {@link PdcAttributeApi} and
 * {@link EmakiAttributeApi} facades.
 *
 * <p>This class holds no business rules of its own: every method delegates to
 * the canonical API. It exists only for the deprecation window so third-party
 * consumers of the CoreLib mirrors keep working, and is removed together with
 * those mirrors.
 *
 * @deprecated Use {@link PdcAttributeApi} and {@link EmakiAttributeApi}.
 */
@Deprecated(forRemoval = true)
public final class LegacyCoreAttributeCompatibility
        implements emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi, EmakiAttributeBridge {

    /**
     * Creates the compatibility adapter. It resolves the canonical facades on
     * each call, so a reloaded EmakiAttribute is never reached through a stale
     * delegate.
     */
    public LegacyCoreAttributeCompatibility() {
    }

    @Override
    public boolean registerSource(String sourceId) {
        return sourceId != null && PdcAttributeApi.registerSource(sourceId);
    }

    @Override
    public void unregisterSource(String sourceId) {
        if (sourceId != null) {
            PdcAttributeApi.unregisterSource(sourceId);
        }
    }

    @Override
    public boolean isRegisteredSource(String sourceId) {
        return sourceId != null && PdcAttributeApi.isRegisteredSource(sourceId);
    }

    @Override
    public Set<String> registeredSources() {
        return PdcAttributeApi.registeredSources();
    }

    @Override
    public boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        if (sourceId == null) {
            return false;
        }
        return PdcAttributeApi.write(itemStack, sourceId, attributes, meta);
    }

    @Override
    public Map<String, PdcAttributePayloadSnapshot> readAllSnapshots(ItemStack itemStack) {
        Map<String, PdcAttributePayload> payloads = PdcAttributeApi.readAll(itemStack);
        if (payloads.isEmpty()) {
            return Map.of();
        }
        Map<String, PdcAttributePayloadSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<String, PdcAttributePayload> entry : payloads.entrySet()) {
            PdcAttributePayload payload = entry.getValue();
            if (payload == null) {
                continue;
            }
            result.put(entry.getKey(), new PdcAttributePayloadSnapshot(
                    payload.sourceId(),
                    payload.attributes(),
                    payload.meta()
            ));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    @Override
    public boolean clear(ItemStack itemStack, String sourceId) {
        return sourceId != null && PdcAttributeApi.clear(itemStack, sourceId);
    }

    @Override
    public void copy(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        PdcAttributeApi.copy(fromItem, toItem, excludedSourceIds);
    }

    @Override
    public boolean available() {
        return EmakiAttributeApi.available();
    }

    @Override
    public double readResourceCurrent(Player player, String resourceId) {
        return EmakiAttributeApi.readResourceCurrent(player, resourceId);
    }

    @Override
    public double readResourceMax(Player player, String resourceId) {
        return EmakiAttributeApi.readResourceMax(player, resourceId);
    }

    @Override
    public boolean consumeResource(Player player, String resourceId, double amount) {
        return EmakiAttributeApi.consumeResource(player, resourceId, amount);
    }

    @Override
    public double readAttributeValue(Player player, String attributeId) {
        return EmakiAttributeApi.readAttributeValue(player, attributeId);
    }

    @Override
    public void scheduleEquipmentSync(Player player) {
        EmakiAttributeApi.scheduleEquipmentSync(player);
    }

    @Override
    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return EmakiAttributeApi.applyDamage(attacker, target, damageTypeId, baseDamage, context);
    }
}
