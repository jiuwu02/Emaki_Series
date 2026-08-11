package emaki.jiuwu.craft.forge.integration.attribute;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.corelib.integration.attribute.AbstractAttributePdcBridge;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;

/**
 * {@link ForgeAttributeBridge} implementation backed by the canonical
 * {@link EmakiAttributeApi} facade.
 *
 * <p>This is the only class in EmakiForge that references EmakiAttributeApi
 * types; it is class-loaded exclusively by {@code ForgeAttributeIntegration}
 * once EmakiAttribute is enabled. Calls always go through the static facade, so
 * a reloaded or disabled EmakiAttribute is never reached through a stale bridge.
 *
 * <p>Source registration and payload guards come from
 * {@link AbstractAttributePdcBridge}; this class only binds those template
 * operations to {@link PdcAttributeAccess}.
 */
public final class EmakiAttributeForgeBridge extends AbstractAttributePdcBridge<PdcAttributeAccess>
        implements ForgeAttributeBridge {

    /**
     * Creates the bridge. Invoked reflectively by
     * {@code ForgeAttributeIntegration} only when EmakiAttribute is enabled.
     *
     * @return the EmakiAttribute-backed bridge
     */
    public static ForgeAttributeBridge create() {
        return new EmakiAttributeForgeBridge();
    }

    private EmakiAttributeForgeBridge() {
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
