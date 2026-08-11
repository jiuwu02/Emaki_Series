package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgePreparationService {

    private final EmakiForgePlugin plugin;
    private final QualityCalculationService qualityCalculationService;
    private final ForgeResultItemFactory resultItemFactory;
    private final ForgeFingerprintService fingerprintService;
    private final PreparedForgeCache preparedForgeCache;

    ForgePreparationService(EmakiForgePlugin plugin,
            QualityCalculationService qualityCalculationService,
            ForgeResultItemFactory resultItemFactory,
            ForgeFingerprintService fingerprintService,
            PreparedForgeCache preparedForgeCache) {
        this.plugin = plugin;
        this.qualityCalculationService = qualityCalculationService;
        this.resultItemFactory = resultItemFactory;
        this.fingerprintService = fingerprintService;
        this.preparedForgeCache = preparedForgeCache;
    }

    ForgeService.PreparedForge prepareForge(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        if (recipe == null) {
            return null;
        }
        DebugLogger debug = plugin.debugLogger();
        UUID playerId = player == null ? null : player.getUniqueId();
        if (debug != null) {
            debug.log("forge", playerId, "forge.start", Map.of(
                    "player", player == null ? "-" : player.getName(),
                    "recipe_id", recipe.id()
            ));
        }
        String cacheKey = fingerprintService.buildPreparationCacheKey(player, recipe, guiItems, previewSeed, forgedAt);
        ForgeService.PreparedForge cached = preparedForgeCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String fingerprint = fingerprintService.buildPreviewFingerprint(player, recipe, guiItems);
        QualityCalculationService.QualityRollPlan rollPlan = qualityCalculationService.resolveQualityRoll(
                player == null ? null : player.getUniqueId(),
                recipe,
                guiItems,
                fingerprintService.buildRollKey(fingerprint, previewSeed)
        );
        if (debug != null) {
            debug.log("forge", playerId, "forge.quality_roll", Map.of(
                    "quality", rollPlan.qualityName(),
                    "multiplier", Numbers.formatNumber(rollPlan.multiplier(), "0.##")
            ));
        }
        EmakiItemAssemblyRequest request = resultItemFactory.buildAssemblyRequest(recipe, guiItems, rollPlan.multiplier(), rollPlan.finalTier(), forgedAt, player);
        if (request == null) {
            if (debug != null) {
                debug.log("forge", playerId, "forge.failed", Map.of("reason", "assembly_request_null"));
            }
            return null;
        }
        ForgeService.PreparedForge preparedForge = new ForgeService.PreparedForge(
                request,
                rollPlan.rolledTier(),
                rollPlan.forceApplied(),
                rollPlan.finalTier(),
                rollPlan.qualityName(),
                rollPlan.multiplier(),
                null
        );
        preparedForgeCache.put(cacheKey, preparedForge);
        return preparedForgeCache.get(cacheKey);
    }
}
