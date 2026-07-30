package emaki.jiuwu.craft.gem.integration.attribute;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.integration.GemAttributeBridge;

/**
 * {@link GemAttributeBridge} implementation backed by the canonical
 * {@link EmakiAttributeApi} facade.
 *
 * <p>This is the only class in EmakiGem that references EmakiAttributeApi types;
 * it is class-loaded exclusively by {@code GemAttributeBridgeHolder} once
 * EmakiAttribute is enabled. Calls always go through the static facade, so a
 * reloaded or disabled EmakiAttribute is never reached through a stale bridge.
 */
public final class EmakiAttributeGemBridge implements GemAttributeBridge {

    private volatile String registeredSourceId;

    /**
     * Creates the bridge. Invoked reflectively by
     * {@code GemAttributeBridgeHolder} only when EmakiAttribute is enabled.
     *
     * @return the EmakiAttribute-backed bridge
     */
    public static GemAttributeBridge create() {
        return new EmakiAttributeGemBridge();
    }

    private EmakiAttributeGemBridge() {
    }

    @Override
    public boolean available() {
        return EmakiAttributeApi.status().usable();
    }

    @Override
    public void syncRegistration(String sourceId) {
        String next = Texts.normalizeId(sourceId);
        String previous = Texts.normalizeId(registeredSourceId);
        PdcAttributeAccess pdc = pdc();
        if (Texts.isNotBlank(previous) && !previous.equals(next)) {
            pdc.unregisterSource(previous);
        }
        if (Texts.isNotBlank(next)) {
            pdc.registerSource(next);
        }
        registeredSourceId = Texts.isNotBlank(next) ? next : null;
    }

    @Override
    public void shutdown() {
        String sourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            pdc().unregisterSource(sourceId);
        }
        registeredSourceId = null;
    }

    @Override
    public boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized) || attributes == null || attributes.isEmpty()) {
            return false;
        }
        PdcAttributeAccess pdc = pdc();
        if (!pdc.isRegisteredSource(normalized) && !pdc.registerSource(normalized).isSuccess()) {
            return false;
        }
        return pdc.write(itemStack, normalized, attributes, meta == null ? Map.of() : meta).isSuccess();
    }

    @Override
    public boolean clear(ItemStack itemStack, String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized)) {
            return false;
        }
        PdcAttributeAccess pdc = pdc();
        return pdc.read(itemStack, normalized).hasValue()
                && pdc.clear(itemStack, normalized).isSuccess();
    }

    @Override
    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        pdc().copy(fromItem, toItem, excludedSourceIds);
    }

    private PdcAttributeAccess pdc() {
        return EmakiAttributeApi.extensions().pdc();
    }
}
