package emaki.jiuwu.craft.strengthen.integration.attribute;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;

/**
 * {@link StrengthenAttributeBridge} implementation backed by the canonical
 * {@link PdcAttributeApi} facade.
 *
 * <p>This is the only class in EmakiStrengthen that references EmakiAttributeApi
 * types; it is class-loaded exclusively by
 * {@code StrengthenAttributeBridgeHolder} once EmakiAttribute is enabled. Calls
 * always go through the static facade, so a reloaded or disabled EmakiAttribute
 * is never reached through a stale bridge.
 */
public final class EmakiAttributeStrengthenBridge implements StrengthenAttributeBridge {

    private volatile String registeredSourceId;

    /**
     * Creates the bridge. Invoked reflectively by
     * {@code StrengthenAttributeBridgeHolder} only when EmakiAttribute is enabled.
     *
     * @return the EmakiAttribute-backed bridge
     */
    public static StrengthenAttributeBridge create() {
        return new EmakiAttributeStrengthenBridge();
    }

    private EmakiAttributeStrengthenBridge() {
    }

    @Override
    public boolean available() {
        return PdcAttributeApi.available();
    }

    @Override
    public void syncRegistration(String sourceId) {
        String next = Texts.normalizeId(sourceId);
        String previous = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(previous) && !previous.equals(next)) {
            PdcAttributeApi.unregisterSource(previous);
        }
        if (Texts.isNotBlank(next)) {
            PdcAttributeApi.registerSource(next);
        }
        registeredSourceId = Texts.isNotBlank(next) ? next : null;
    }

    @Override
    public void shutdown() {
        String sourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            PdcAttributeApi.unregisterSource(sourceId);
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
        if (!PdcAttributeApi.isRegisteredSource(normalized) && !PdcAttributeApi.registerSource(normalized)) {
            return false;
        }
        return PdcAttributeApi.write(itemStack, normalized, attributes, meta == null ? Map.of() : meta);
    }

    @Override
    public boolean clear(ItemStack itemStack, String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized)) {
            return false;
        }
        return PdcAttributeApi.read(itemStack, normalized) != null
                && PdcAttributeApi.clear(itemStack, normalized);
    }

    @Override
    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        PdcAttributeApi.copy(fromItem, toItem, excludedSourceIds);
    }
}
