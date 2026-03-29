package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ForgeService {

    public record PreparedForge(EmakiItemAssemblyRequest request,
                                emaki.jiuwu.craft.forge.model.QualitySettings.QualityTier qualityTier,
                                String quality,
                                double multiplier,
                                ItemStack previewItem) {
        public PreparedForge {
            previewItem = previewItem == null ? null : previewItem.clone();
        }
    }

    private final EmakiForgePlugin plugin;
    private final ForgeResultItemFactory resultItemFactory;
    private final ForgeActionCoordinator actionCoordinator;
    private final ForgeLookupIndex lookupIndex;
    private final QualityCalculationService qualityCalculationService;
    private final MaterialValidationService materialValidationService;
    private final RecipeMatchingService recipeMatchingService;
    private final ForgeExecutionService forgeExecutionService;

    public ForgeService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.resultItemFactory = new ForgeResultItemFactory(plugin);
        this.actionCoordinator = new ForgeActionCoordinator(plugin, resultItemFactory);
        this.lookupIndex = new ForgeLookupIndex(plugin);
        this.materialValidationService = new MaterialValidationService(plugin, lookupIndex);
        this.qualityCalculationService = new QualityCalculationService(
            () -> plugin.appConfig().qualitySettings(),
            new QualityCalculationService.GuaranteeCounterStore() {
                @Override
                public int counter(UUID playerId, String key) {
                    return plugin.playerDataStore().guaranteeCounter(playerId, key);
                }

                @Override
                public void increment(UUID playerId, String key) {
                    plugin.playerDataStore().incrementGuaranteeCounter(playerId, key);
                }

                @Override
                public void reset(UUID playerId, String key) {
                    plugin.playerDataStore().resetGuaranteeCounter(playerId, key);
                }
            }
        );
        this.recipeMatchingService = new RecipeMatchingService(
            lookupIndex::sortedRecipes,
            (player, recipe) -> guiItems -> canForge(player, recipe, guiItems)
        );
        this.forgeExecutionService = new ForgeExecutionService(
            actionCoordinator,
            qualityCalculationService,
            (player, recipe, guiItems, preparedForge) -> preparedForge == null
                ? prepareForge(player, recipe, guiItems, 0L, System.currentTimeMillis())
                : preparedForge,
            (player, request) -> {
                EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
                return coreLib == null ? null : coreLib.itemAssemblyService().give(player, request);
            },
            (playerId, recipeId) -> plugin.playerDataStore().recordCraft(playerId, recipeId)
        );
    }

    public void refreshIndexes() {
        lookupIndex.refresh();
    }

    public List<Recipe> sortedRecipes() {
        return lookupIndex.sortedRecipes();
    }

    public ForgeMaterial findMaterialBySource(ItemSource source) {
        return lookupIndex.findMaterialBySource(source);
    }

    public Blueprint findBlueprintBySource(ItemSource source) {
        return lookupIndex.findBlueprintBySource(source);
    }

    public RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems) {
        return recipeMatchingService.findMatchingRecipe(player, guiItems);
    }

    public ValidationResult canForge(Player player, Recipe recipe, GuiItems guiItems) {
        if (recipe == null) {
            return ValidationResult.fail("forge.error.no_recipe");
        }
        AppConfig config = plugin.appConfig();
        if (recipe.requiresPermission()
            && !(config.opBypass() && player.isOp())
            && !player.hasPermission(recipe.permission())) {
            return ValidationResult.fail("forge.error.permission_denied");
        }
        if (!recipe.conditions().isEmpty()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                recipe.conditions(),
                recipe.conditionType(),
                recipe.conditionRequiredCount(),
                text -> replacePlaceholders(player, text),
                config.invalidAsFailure()
            );
            if (!conditionsPassed) {
                return ValidationResult.fail("forge.error.condition_not_met");
            }
        }
        if (recipe.targetItemSource() != null) {
            if (guiItems.targetItem() == null) {
                return ValidationResult.fail("forge.error.no_target_item");
            }
            if (!plugin.itemIdentifierService().matchesSource(guiItems.targetItem(), recipe.targetItemSource())) {
                return ValidationResult.fail("forge.error.invalid_target_item");
            }
        }
        return materialValidationService.validate(recipe, guiItems);
    }

    public String buildPreviewFingerprint(Player player, Recipe recipe, GuiItems guiItems) {
        List<Object> parts = new ArrayList<>();
        parts.add(player == null ? "" : player.getUniqueId().toString());
        parts.add(recipe == null ? "" : recipe.id());
        parts.add(player == null || recipe == null ? 0 : plugin.playerDataStore().guaranteeCounter(player.getUniqueId(), recipe.id()));
        appendItemSignature(parts, "target", guiItems == null ? null : guiItems.targetItem());
        appendMappedSignatures(parts, "blueprint", guiItems == null ? null : guiItems.blueprints());
        appendMappedSignatures(parts, "required", guiItems == null ? null : guiItems.requiredMaterials());
        appendMappedSignatures(parts, "optional", guiItems == null ? null : guiItems.optionalMaterials());
        return SignatureUtil.stableSignature(parts);
    }

    public ItemStack previewResultItem(Player player,
                                       Recipe recipe,
                                       GuiItems guiItems,
                                       long previewSeed,
                                       long forgedAt) {
        PreparedForge preparedForge = prepareForge(player, recipe, guiItems, previewSeed, forgedAt);
        return preparedForge == null || preparedForge.previewItem() == null ? null : preparedForge.previewItem().clone();
    }

    public PreparedForge prepareForge(Player player,
                                      Recipe recipe,
                                      GuiItems guiItems,
                                      long previewSeed,
                                      long forgedAt) {
        if (recipe == null) {
            return null;
        }
        QualityCalculationService.QualityRollPlan rollPlan = qualityCalculationService.resolveQualityRoll(
            player == null ? null : player.getUniqueId(),
            recipe,
            buildRollKey(buildPreviewFingerprint(player, recipe, guiItems), previewSeed)
        );
        EmakiItemAssemblyRequest request = resultItemFactory.buildAssemblyRequest(recipe, guiItems, rollPlan.multiplier(), rollPlan.tier(), forgedAt);
        if (request == null) {
            return null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        ItemStack previewItem = coreLib == null ? null : coreLib.itemAssemblyService().preview(request);
        return new PreparedForge(request, rollPlan.tier(), rollPlan.qualityName(), rollPlan.multiplier(), previewItem);
    }

    public CompletableFuture<ForgeResult> executeForgeAsync(Player player,
                                                            Recipe recipe,
                                                            GuiItems guiItems,
                                                            PreparedForge preparedForge) {
        ValidationResult validation = canForge(player, recipe, guiItems);
        return forgeExecutionService.execute(player, recipe, guiItems, preparedForge, validation);
    }

    private String buildRollKey(String fingerprint, long previewSeed) {
        return SignatureUtil.combine(fingerprint, Long.toUnsignedString(previewSeed));
    }

    private void appendMappedSignatures(List<Object> parts, String prefix, Map<Integer, ItemStack> items) {
        if (parts == null || items == null || items.isEmpty()) {
            return;
        }
        List<Integer> slots = new ArrayList<>(items.keySet());
        slots.sort(Integer::compareTo);
        for (Integer slot : slots) {
            appendItemSignature(parts, prefix + ":" + slot, items.get(slot));
        }
    }

    private void appendItemSignature(List<Object> parts, String prefix, ItemStack itemStack) {
        if (parts == null || itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        parts.add(prefix);
        parts.add(source == null ? "" : ItemSourceUtil.toShorthand(source));
        parts.add(itemStack.getAmount());
    }

    public String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        return resultItemFactory.resolveResultItemName(recipe, itemStack);
    }

    private String replacePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        return Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, text));
    }
}
