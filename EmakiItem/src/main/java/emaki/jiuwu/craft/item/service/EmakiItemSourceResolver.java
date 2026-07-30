package emaki.jiuwu.craft.item.service;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceProbe;
import emaki.jiuwu.craft.corelib.item.ItemSourceProbeStatus;
import emaki.jiuwu.craft.corelib.item.ItemSourceResolver;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class EmakiItemSourceResolver implements ItemSourceResolver {

    private final EmakiItemApi.Bridge api;

    public EmakiItemSourceResolver(EmakiItemApi.Bridge api) {
        this.api = api;
    }

    @Override
    public String id() {
        return "emakiitem";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.EMAKIITEM;
    }

    @Override
    public boolean isAvailable(ItemSource source) {
        return probe(source).ready();
    }

    @Override
    public ItemSourceProbe probe(ItemSource source) {
        if (!supports(source) || Texts.isBlank(source.getIdentifier())) {
            return ItemSourceProbe.of(
                    ItemSourceProbeStatus.INVALID_SOURCE,
                    source,
                    id(),
                    "The EmakiItem source type and identifier are required."
            );
        }
        try {
            if (!api.status().usable()) {
                return ItemSourceProbe.of(
                        ItemSourceProbeStatus.PROVIDER_NOT_READY,
                        source,
                        id(),
                        "EmakiItem is installed but its runtime is not ready."
                );
            }
            return api.catalog().exists(source.getIdentifier())
                    ? ItemSourceProbe.ready(source, id())
                    : ItemSourceProbe.of(
                    ItemSourceProbeStatus.SOURCE_NOT_FOUND,
                    source,
                    id(),
                    "EmakiItem does not contain the requested item definition."
            );
        } catch (LinkageError exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.INCOMPATIBLE, source, id(), detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.RESOLUTION_ERROR, source, id(), detail(exception));
        }
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        if (!readyForAccess()) {
            return null;
        }
        String definitionId = api.catalog().identify(itemStack).orElse(null);
        return Texts.isBlank(definitionId) ? null : new ItemSource(ItemSourceType.EMAKIITEM, definitionId);
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        if (!supports(source) || !readyForAccess()) {
            return null;
        }
        return api.operations().create(source.getIdentifier(), amount).orElse(null);
    }

    @Override
    public String displayName(ItemSource source) {
        if (!supports(source) || !readyForAccess()) {
            return null;
        }
        return api.catalog().displayName(source.getIdentifier()).orElse(null);
    }

    private boolean readyForAccess() {
        try {
            return api.status().usable();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private String detail(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable == null ? "Unknown EmakiItem resolution failure" : throwable.getClass().getSimpleName()
                : message;
    }
}
