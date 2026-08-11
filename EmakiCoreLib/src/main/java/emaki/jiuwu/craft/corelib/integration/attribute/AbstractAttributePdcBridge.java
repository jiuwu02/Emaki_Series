package emaki.jiuwu.craft.corelib.integration.attribute;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Shared source-registration and payload-guard mechanics for module-side attribute PDC bridges.
 *
 * <p>EmakiForge, EmakiGem and EmakiStrengthen each grew a byte-identical copy of this machinery:
 * the same {@code volatile} registered-source field, the same {@link Texts#normalizeId(Object)}
 * normalisation, the same unregister-previous-then-register-next ordering, the same blank guards and
 * the same register-on-demand-before-write fallback. Only the surrounding module interface and the
 * concrete attribute access type differed, so the mechanics live here and modules keep just the
 * attribute-api binding plus their own business capabilities.
 *
 * <p>CoreLib must not depend on {@code emaki-attribute-api}, so the attribute access handle stays an
 * opaque type parameter and every call touching it is delegated to the module subclass. The subclass
 * therefore remains the only class in its module that references EmakiAttributeApi types, preserving
 * the lazy class-loading isolation each bridge holder relies on.
 *
 * <h2>Single-lookup guarantee</h2>
 *
 * <p>Every operation that needs the handle resolves it through {@link #access()} exactly once and
 * threads that one instance through each delegated call, so a concurrent EmakiAttribute reload can
 * never split one logical operation across two facade instances. {@link #available()} resolves no
 * handle at all, and {@link #shutdown()} resolves one only when a registration is actually held.
 *
 * @param <A> module-side attribute access handle type; opaque to CoreLib
 */
public abstract class AbstractAttributePdcBridge<A> {

    private volatile String registeredSourceId;

    /** Creates the bridge template. */
    protected AbstractAttributePdcBridge() {
    }

    /** {@return the attribute access handle, resolved fresh for each operation} */
    protected abstract A access();

    /** {@return whether the backing attribute runtime is usable} */
    protected abstract boolean usable();

    /**
     * Registers a source id.
     *
     * @param access the handle resolved for this operation
     * @param sourceId the normalized source id
     * @return {@code true} when registration succeeded
     */
    protected abstract boolean registerSource(A access, String sourceId);

    /**
     * Releases a source id; the outcome is intentionally ignored by callers.
     *
     * @param access the handle resolved for this operation
     * @param sourceId the normalized source id
     */
    protected abstract void unregisterSource(A access, String sourceId);

    /**
     * {@return whether the source id is currently registered}
     *
     * @param access the handle resolved for this operation
     * @param sourceId the normalized source id
     */
    protected abstract boolean isRegisteredSource(A access, String sourceId);

    /**
     * Writes an attribute payload.
     *
     * @param access the handle resolved for this operation
     * @param itemStack the target item
     * @param sourceId the normalized owning source id
     * @param attributes attribute id to value mapping
     * @param meta string metadata, never {@code null}
     * @return {@code true} when the item was modified
     */
    protected abstract boolean writePayload(A access,
            ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta);

    /**
     * {@return whether a payload for the source is present on the item}
     *
     * @param access the handle resolved for this operation
     * @param itemStack the target item
     * @param sourceId the normalized source id
     */
    protected abstract boolean hasPayload(A access, ItemStack itemStack, String sourceId);

    /**
     * Removes the payload of a single source.
     *
     * @param access the handle resolved for this operation
     * @param itemStack the target item
     * @param sourceId the normalized source id
     * @return {@code true} when data was removed
     */
    protected abstract boolean clearPayload(A access, ItemStack itemStack, String sourceId);

    /** {@return whether the backing attribute runtime is available for attribute operations} */
    public final boolean available() {
        return usable();
    }

    /**
     * Registers an attribute PDC source id, replacing any previous registration.
     *
     * @param sourceId the source id to register
     */
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

    /** Releases the source id currently registered through this bridge. */
    public final void shutdown() {
        String sourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            unregisterSource(access(), sourceId);
        }
        registeredSourceId = null;
    }

    /**
     * Writes an attribute payload for a source, registering the source on demand.
     *
     * @param itemStack the target item
     * @param sourceId the owning source id
     * @param attributes attribute id to value mapping
     * @param meta string metadata
     * @return {@code true} when the item was modified
     */
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

    /**
     * Removes the payload of a single source when one is present.
     *
     * @param itemStack the target item
     * @param sourceId the source id to clear
     * @return {@code true} when data was removed
     */
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
