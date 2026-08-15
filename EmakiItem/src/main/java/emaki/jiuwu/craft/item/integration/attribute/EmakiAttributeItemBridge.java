package emaki.jiuwu.craft.item.integration.attribute;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.integration.ItemAttributeBridge;

public final class EmakiAttributeItemBridge implements ItemAttributeBridge {

    private volatile String registeredSourceId;

    public static ItemAttributeBridge create() {
        return new EmakiAttributeItemBridge();
    }

    private EmakiAttributeItemBridge() {
    }

    @Override
    public boolean available() {
        return EmakiAttributeApi.status().usable();
    }

    @Override
    public void syncRegistration(String sourceId) {
        String next = normalize(sourceId);
        String previous = normalize(registeredSourceId);
        PdcAttributeAccess pdc = pdc();
        if (!previous.isEmpty() && !previous.equals(next)) {
            pdc.unregisterSource(previous);
        }
        if (!next.isEmpty()) {
            pdc.registerSource(next);
        }
        registeredSourceId = next.isEmpty() ? null : next;
    }

    @Override
    public void shutdown() {
        String sourceId = normalize(registeredSourceId);
        if (!sourceId.isEmpty()) {
            pdc().unregisterSource(sourceId);
        }
        registeredSourceId = null;
    }

    @Override
    public boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        String normalized = normalize(sourceId);
        if (itemStack == null || normalized.isEmpty() || attributes == null || attributes.isEmpty()) {
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
        String normalized = normalize(sourceId);
        if (itemStack == null || normalized.isEmpty()) {
            return false;
        }
        PdcAttributeAccess pdc = pdc();
        return pdc.read(itemStack, normalized).hasValue()
                && pdc.clear(itemStack, normalized).isSuccess();
    }

    @Override
    public Map<String, Double> readAttributes(ItemStack itemStack, String sourceId) {
        PdcAttributePayload payload = readPayload(itemStack, sourceId);
        return payload == null ? Map.of() : payload.attributes();
    }

    @Override
    public Map<String, String> readMeta(ItemStack itemStack, String sourceId) {
        PdcAttributePayload payload = readPayload(itemStack, sourceId);
        return payload == null ? Map.of() : payload.meta();
    }

    @Override
    public boolean hasPayload(ItemStack itemStack, String sourceId) {
        return readPayload(itemStack, sourceId) != null;
    }

    @Override
    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        pdc().copy(fromItem, toItem, excludedSourceIds);
    }

    @Override
    public void scheduleEquipmentSync(Player player) {
        EmakiAttributeApi.operations().scheduleEquipmentSync(player);
    }

    private PdcAttributePayload readPayload(ItemStack itemStack, String sourceId) {
        String normalized = normalize(sourceId);
        if (itemStack == null || normalized.isEmpty()) {
            return null;
        }
        return pdc().read(itemStack, normalized).orElse(null);
    }

    private PdcAttributeAccess pdc() {
        return EmakiAttributeApi.extensions().pdc();
    }

    private String normalize(String sourceId) {
        return Texts.normalizeId(sourceId);
    }
}
