package emaki.jiuwu.craft.item.apiimpl;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemExtensions;
import emaki.jiuwu.craft.item.api.effect.ItemEffectRegistration;
import emaki.jiuwu.craft.item.api.effect.ItemEffectType;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewRegistration;
import emaki.jiuwu.craft.item.service.EmakiItemEffectRegistry;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewRegistry;

public final class DefaultItemExtensions implements ItemExtensions {

    private final EmakiItemPlugin plugin;

    public DefaultItemExtensions(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull ItemLayerPreviewRegistration registerLayerPreview(@Nullable Plugin owner,
            @Nullable ItemLayerPreviewProvider provider) {
        EmakiItemLayerPreviewRegistry registry = plugin.layerPreviewRegistry();
        if (registry == null || owner == null || provider == null) {
            return ItemLayerPreviewRegistration.noop();
        }
        return registry.register(owner, provider);
    }

    @Override
    public void unregisterLayerPreviews(@Nullable Plugin owner) {
        EmakiItemLayerPreviewRegistry registry = plugin.layerPreviewRegistry();
        if (registry != null && owner != null) {
            registry.unregisterOwner(owner);
        }
    }

    @Override
    public @NotNull ItemEffectRegistration registerEffectType(@Nullable Plugin owner, @Nullable ItemEffectType type) {
        EmakiItemEffectRegistry registry = plugin.effectRegistry();
        if (registry == null || owner == null || type == null) {
            return ItemEffectRegistration.noop();
        }
        return registry.register(owner, type);
    }

    @Override
    public void unregisterEffectTypes(@Nullable Plugin owner) {
        EmakiItemEffectRegistry registry = plugin.effectRegistry();
        if (registry != null && owner != null) {
            registry.unregisterOwner(owner);
        }
    }
}
