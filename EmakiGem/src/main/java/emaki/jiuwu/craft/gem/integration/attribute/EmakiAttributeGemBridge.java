package emaki.jiuwu.craft.gem.integration.attribute;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.corelib.integration.attribute.AbstractAttributePdcBridge;
import emaki.jiuwu.craft.gem.integration.GemAttributeBridge;

public final class EmakiAttributeGemBridge extends AbstractAttributePdcBridge<PdcAttributeAccess>
        implements GemAttributeBridge {

    public static GemAttributeBridge create() {
        return new EmakiAttributeGemBridge();
    }

    private EmakiAttributeGemBridge() {
    }

    @Override
    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        access().copy(fromItem, toItem, excludedSourceIds);
    }

    @Override
    protected PdcAttributeAccess access() {
        return EmakiAttributeApi.extensions().pdc();
    }

    @Override
    protected boolean usable() {
        return EmakiAttributeApi.status().usable();
    }

    @Override
    protected boolean registerSource(PdcAttributeAccess pdc, String sourceId) {
        return pdc.registerSource(sourceId).isSuccess();
    }

    @Override
    protected void unregisterSource(PdcAttributeAccess pdc, String sourceId) {
        pdc.unregisterSource(sourceId);
    }

    @Override
    protected boolean isRegisteredSource(PdcAttributeAccess pdc, String sourceId) {
        return pdc.isRegisteredSource(sourceId);
    }

    @Override
    protected boolean writePayload(PdcAttributeAccess pdc,
            ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return pdc.write(itemStack, sourceId, attributes, meta).isSuccess();
    }

    @Override
    protected boolean hasPayload(PdcAttributeAccess pdc, ItemStack itemStack, String sourceId) {
        return pdc.read(itemStack, sourceId).hasValue();
    }

    @Override
    protected boolean clearPayload(PdcAttributeAccess pdc, ItemStack itemStack, String sourceId) {
        return pdc.clear(itemStack, sourceId).isSuccess();
    }
}
