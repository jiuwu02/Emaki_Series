package emaki.jiuwu.craft.forge.service;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.cache.CacheManager;

final class PreparedForgeCache {

    private static final int CACHE_SIZE = 128;
    private static final long CACHE_TTL_MILLIS = 30_000L;

    private final CacheManager<String, ForgeService.PreparedForge> cache =
            new CacheManager<>(CACHE_SIZE, CACHE_TTL_MILLIS);

    ForgeService.PreparedForge get(String key) {
        return copy(cache.get(key));
    }

    void put(String key, ForgeService.PreparedForge preparedForge) {
        cache.put(key, copy(preparedForge));
    }

    void clear() {
        cache.clear();
    }

    private ForgeService.PreparedForge copy(ForgeService.PreparedForge source) {
        if (source == null) {
            return null;
        }
        EmakiItemAssemblyRequest request = copyAssemblyRequest(source.request());
        return new ForgeService.PreparedForge(
                request,
                source.rolledQualityTier(),
                source.forceQualityApplied(),
                source.qualityTier(),
                source.quality(),
                source.multiplier(),
                source.previewItem() == null ? null : source.previewItem().clone()
        );
    }

    private EmakiItemAssemblyRequest copyAssemblyRequest(EmakiItemAssemblyRequest request) {
        if (request == null) {
            return null;
        }
        ItemStack existingItem = request.existingItem() == null ? null : request.existingItem().clone();
        return new EmakiItemAssemblyRequest(
                request.baseSource(),
                request.amount(),
                existingItem,
                request.layerSnapshots(),
                request.feedbackPlayerId()
        );
    }
}
