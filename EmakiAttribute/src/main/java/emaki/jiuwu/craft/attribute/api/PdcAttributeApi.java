package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;

public interface PdcAttributeApi {

    boolean registerSource(String sourceId);

    void unregisterSource(String sourceId);

    boolean isRegisteredSource(String sourceId);

    Set<String> registeredSources();

    boolean write(ItemStack itemStack, PdcAttributePayload payload);

    default boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return write(itemStack, PdcAttributePayload.of(sourceId, attributes, meta));
    }

    PdcAttributePayload read(ItemStack itemStack, String sourceId);

    Map<String, PdcAttributePayload> readAll(ItemStack itemStack);

    boolean clear(ItemStack itemStack, String sourceId);

    void clearAll(ItemStack itemStack);
}
