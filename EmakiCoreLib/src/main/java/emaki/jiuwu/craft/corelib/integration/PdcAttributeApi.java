package emaki.jiuwu.craft.corelib.integration;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

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
}
