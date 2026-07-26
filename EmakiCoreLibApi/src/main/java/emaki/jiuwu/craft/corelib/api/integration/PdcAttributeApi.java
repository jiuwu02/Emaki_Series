package emaki.jiuwu.craft.corelib.api.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * Legacy CoreLib mirror of EmakiAttribute's PDC attribute contract.
 *
 * <p>This mirror is lossy: {@link #readAllSnapshots(ItemStack)} exposes only
 * attributes and meta, dropping conditions, schema version and update timestamp.
 *
 * @deprecated Superseded by {@code emaki.jiuwu.craft.attribute.api.PdcAttributeApi}
 *             and the full {@code PdcAttributePayload} in EmakiAttributeApi, which
 *             are the canonical contract. The only remaining implementation is
 *             EmakiAttribute's compatibility adapter, which merely delegates to
 *             that facade. Retained for one synchronized release window.
 */
@Deprecated(forRemoval = true)
public interface PdcAttributeApi {

    boolean registerSource(String sourceId);

    void unregisterSource(String sourceId);

    boolean isRegisteredSource(String sourceId);

    Set<String> registeredSources();

    boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta);

    Map<String, PdcAttributePayloadSnapshot> readAllSnapshots(ItemStack itemStack);

    boolean clear(ItemStack itemStack, String sourceId);

    /**
     * Copies every stored payload from one item to another, overwriting the
     * destination per source id and skipping {@code excludedSourceIds}.
     *
     * <p>Unlike {@link #readAllSnapshots(ItemStack)} this transfers full
     * payloads, so conditions, schema version and update timestamp survive.
     *
     * @param fromItem the item to read payloads from
     * @param toItem the item to write payloads to
     * @param excludedSourceIds source ids to skip; may be {@code null}
     */
    void copy(ItemStack fromItem, ItemStack toItem, java.util.Set<String> excludedSourceIds);
}
