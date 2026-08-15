package emaki.jiuwu.craft.corelib.integration.attribute;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public abstract class AbstractAttributePdcBridge<A> {

    private volatile String registeredSourceId;

    protected AbstractAttributePdcBridge() {
    }

    protected abstract A access();

    protected abstract boolean usable();

    protected abstract boolean registerSource(A access, String sourceId);

    protected abstract void unregisterSource(A access, String sourceId);

    protected abstract boolean isRegisteredSource(A access, String sourceId);

    protected abstract boolean writePayload(A access,
            ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta);

    protected abstract boolean hasPayload(A access, ItemStack itemStack, String sourceId);

    protected abstract boolean clearPayload(A access, ItemStack itemStack, String sourceId);

    public final boolean available() {
        return usable();
    }

    public final void syncRegistration(String sourceId) {
        String next = Texts.normalizeId(sourceId);
        String previous = Texts.normalizeId(registeredSourceId);
        A access = access();
        if (Texts.isNotBlank(previous) && !previous.equals(next)) {
            unregisterSource(access, previous);
        }
        if (Texts.isNotBlank(next)) {
            registerSource(access, next);
        }
        registeredSourceId = Texts.isNotBlank(next) ? next : null;
    }

    public final void shutdown() {
        String sourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            unregisterSource(access(), sourceId);
        }
        registeredSourceId = null;
    }

    public final boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized) || attributes == null || attributes.isEmpty()) {
            return false;
        }
        A access = access();
        if (!isRegisteredSource(access, normalized) && !registerSource(access, normalized)) {
            return false;
        }
        return writePayload(access, itemStack, normalized, attributes, meta == null ? Map.of() : meta);
    }

    public final boolean clear(ItemStack itemStack, String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized)) {
            return false;
        }
        A access = access();
        return hasPayload(access, itemStack, normalized)
                && clearPayload(access, itemStack, normalized);
    }
}
