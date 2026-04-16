package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenAttemptService implements EmakiStrengthenApi {

    private static final String PDC_ATTRIBUTE_SOURCE_ID = "strengthen";

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenRecipeResolver recipeResolver;
    private final ChanceCalculator chanceCalculator;
    private final StrengthenEconomyService economyService;
    private final StrengthenSnapshotBuilder snapshotBuilder;
    private final StrengthenActionCoordinator actionCoordinator;
    private final EmakiItemAssemblyService itemAssemblyService;
    private final ItemSourceService itemSourceService;

    public StrengthenAttemptService(EmakiStrengthenPlugin plugin,
            StrengthenRecipeResolver recipeResolver,
            ChanceCalculator chanceCalculator,
            StrengthenEconomyService economyService,
            StrengthenSnapshotBuilder snapshotBuilder,
            StrengthenActionCoordinator actionCoordinator,
            EmakiItemAssemblyService itemAssemblyService,
            ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.recipeResolver = recipeResolver;
        this.chanceCalculator = chanceCalculator;
        this.economyService = economyService;
        this.snapshotBuilder = snapshotBuilder;
        this.actionCoordinator = actionCoordinator;
        this.itemAssemblyService = itemAssemblyService;
        this.itemSourceService = itemSourceService;
    }

    @Override
    public boolean canStrengthen(ItemStack itemStack) {
        return readState(itemStack).eligible();
    }

    @Override
    public StrengthenState readState(ItemStack itemStack) {
        return resolveState(itemStack).state();
    }

    private ResolvedState resolveState(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new ResolvedState(StrengthenState.ineligible("strengthen.error.no_target", null, ""), StoredState.empty(null, ""));
        }
        ItemSource initialBaseSource = recipeResolver.resolveBaseSource(itemStack);
        String initialSignature = ItemSourceUtil.toShorthand(initialBaseSource);
        StoredState stored = readStoredState(itemStack, initialBaseSource, initialSignature);
        StrengthenRecipeResolver.ResolvedItem resolved = recipeResolver.resolve(itemStack, stored.recipeId());
        if (resolved.baseSource() == null) {
            return new ResolvedState(StrengthenState.ineligible("strengthen.error.no_source", null, ""), stored);
        }
        stored = stored.withBaseSourceSignature(resolved.baseSourceSignature());
        String recipeId = Texts.isNotBlank(stored.recipeId()) ? stored.recipeId() : resolved.recipeId();
        boolean eligible = Texts.isNotBlank(recipeId) && plugin.recipeLoader().get(recipeId) != null;
        String reason = eligible ? "" : "strengthen.error.no_recipe";
        return new ResolvedState(
                new StrengthenState(
                        eligible,
                        reason,
                        stored.hasLayer(),
                        resolved.baseSource(),
                        resolved.baseSourceSignature(),
                        recipeId,
                        stored.currentStar(),
                        stored.crackLevel(),
                        stored.firstReachFlags(),
                        stored.successCount(),
                        stored.failureCount(),
                        stored.lastAttemptAt()
                ),
                stored
        );
    }

    @Override
    public AttemptPreview preview(Player player, AttemptContext context) {
        ItemStack targetItem = context == null ? null : context.targetItem();
        StrengthenState state = readState(targetItem);
        if (!state.eligible()) {
            return ineligiblePreview(state.eligibleReason(), state);
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(state.recipeId());
        if (recipe == null) {
            return ineligiblePreview("strengthen.error.no_recipe", state);
        }
        if (state.currentStar() >= recipe.limits().maxStar()) {
            return ineligiblePreview("strengthen.error.already_max", state, recipe);
        }

        int targetStar = state.currentStar() + 1;
        StrengthenRecipe.StarStage stage = recipe.stage(targetStar);
        if (stage == null) {
            return ineligiblePreview("strengthen.error.already_max", state, recipe);
        }

        MaterialPlan materials = resolveMaterialPlan(player, context, stage);
        if (Texts.isNotBlank(materials.errorKey())) {
            return new AttemptPreview(false, materials.errorKey(), state, recipe, state.currentStar(), targetStar, 0D, List.of(),
                    state.currentStar(), state.temperLevel(), false, 0, Map.of(), Set.of(), materials.requiredMaterials(), materials.optionalMaterials());
        }

        double successRate = chanceCalculator.calculateSuccessRate(plugin.appConfig(), recipe, state.currentStar(), state.temperLevel(),
                materials.appliedTemperBonus());
        ChanceCalculator.FailureResolution failure = chanceCalculator.resolveFailure(recipe, state.currentStar(), state.temperLevel(),
                materials.appliedTemperBonus(), materials.protectionApplied());
        Set<Integer> firstReachStars = collectFirstReach(state.firstReachFlags(), targetStar);
        return new AttemptPreview(
                true,
                "",
                state,
                recipe,
                state.currentStar(),
                targetStar,
                successRate,
                economyService.quoteCosts(recipe, targetStar),
                failure.resultingStar(),
                failure.resultingTemper(),
                materials.protectionApplied(),
                materials.appliedTemperBonus(),
                recipe.deltaStats(state.currentStar(), targetStar),
                firstReachStars,
                materials.requiredMaterials(),
                materials.optionalMaterials()
        );
    }

    @Override
    public AttemptResult attempt(Player player, AttemptContext context) {
        AttemptPreview preview = preview(player, context);
        if (!preview.eligible()) {
            return AttemptResult.failure(preview.errorKey(), preview, replacements(preview, preview.currentStar()));
        }

        boolean success = ThreadLocalRandom.current().nextDouble(100D) < preview.successRate();
        StrengthenState currentState = preview.state();
        int resultStar = success ? preview.targetStar() : preview.failureStar();
        int resultTemper = success ? 0 : preview.failureTemper();
        StarProgress progress = collectStarProgress(currentState.firstReachFlags(), success ? Set.of(preview.targetStar()) : Set.of());

        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                currentState.baseSource(),
                currentState.baseSourceSignature(),
                preview.recipe().id(),
                resultStar,
                resultTemper,
                progress.updatedFlags(),
                currentState.successCount() + (success ? 1 : 0),
                currentState.failureCount() + (success ? 0 : 1),
                System.currentTimeMillis()
        );

        ItemStack rebuilt = rebuildWithState(context == null ? null : context.targetItem(), updated, buildMaterialsSignature(preview));
        if (rebuilt == null) {
            return AttemptResult.failure("strengthen.error.rebuild_failed", preview, replacements(preview, resultStar));
        }

        if (!consumeRequiredInventoryMaterials(player, preview.requiredMaterials())) {
            return AttemptResult.failure("strengthen.error.material_missing", preview, replacements(preview, preview.currentStar()));
        }

        StrengthenEconomyService.ChargeResult chargeResult = economyService.charge(player, preview.costs());
        if (!chargeResult.success()) {
            refundRequiredInventoryMaterials(player, preview.requiredMaterials());
            return AttemptResult.failure(chargeResult.errorKey(), preview, replacements(preview, preview.currentStar()));
        }

        return new AttemptResult(success, "", replacements(preview, resultStar), preview, rebuilt, resultStar, resultTemper, progress.newlyReached());
    }

    @Override
    public ItemStack rebuild(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        ResolvedState resolvedState = resolveState(itemStack);
        StrengthenState state = resolvedState.state();
        if (!state.hasLayer() || Texts.isBlank(state.recipeId())) {
            return itemStack;
        }
        return rebuildWithState(itemStack, state, resolvedState.stored().materialsSignature());
    }

    public ItemStack applyAdminState(ItemStack itemStack, Integer star, Integer temper, String recipeId) {
        StrengthenState current = readState(itemStack);
        if (current.baseSource() == null) {
            return null;
        }
        String effectiveRecipe = Texts.isNotBlank(recipeId) ? recipeId : current.recipeId();
        StrengthenRecipe recipe = plugin.recipeLoader().get(effectiveRecipe);
        if (recipe == null) {
            return null;
        }
        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                current.baseSource(),
                current.baseSourceSignature(),
                effectiveRecipe,
                star == null ? current.currentStar() : Numbers.clamp(star, 0, recipe.limits().maxStar()),
                temper == null ? current.temperLevel() : Numbers.clamp(temper, 0, recipe.limits().maxTemper()),
                current.milestoneFlags(),
                current.successCount(),
                current.failureCount(),
                System.currentTimeMillis()
        );
        return rebuildWithState(itemStack, updated, readStoredState(itemStack, current.baseSource(), current.baseSourceSignature()).materialsSignature());
    }

    public void triggerSuccessActions(Player player, StrengthenRecipe recipe, String resultSlotId, ItemStack resultItem, int star, int temper) {
        actionCoordinator.triggerSuccessActions(player, recipe, resultSlotId, resultItem, star, temper);
    }

    public void triggerFailureActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            ItemStack resultItem,
            int wasStar,
            int resultStar,
            int temper,
            boolean dropped,
            boolean protectionApplied) {
        actionCoordinator.triggerFailureActions(player, recipe, resultSlotId, resultItem, wasStar, resultStar, temper, dropped, protectionApplied);
    }

    public void broadcastFirstReach(Player player, ItemStack resultItem, Set<Integer> newlyReached) {
        if (player == null || newlyReached == null || newlyReached.isEmpty()) {
            return;
        }
        String showItem = actionCoordinator.buildShowItem(resultItem);
        if (newlyReached.contains(8)) {
            String message = plugin.messageService().message("strengthen.broadcast.local_reach", Map.of(
                    "player", player.getName(),
                    "show_item", showItem,
                    "star", 8
            ));
            double radius = plugin.appConfig().localBroadcastRadius();
            double radiusSquared = radius * radius;
            var world = player.getWorld();
            var playerLocation = player.getLocation();
            world.getPlayers().stream()
                    .filter(viewer -> viewer.getLocation().distanceSquared(playerLocation) <= radiusSquared)
                    .forEach(viewer -> plugin.messageService().sendRaw(viewer, message));
        }
        for (int star : List.of(10, 12)) {
            if (!newlyReached.contains(star)) {
                continue;
            }
            String message = plugin.messageService().message("strengthen.broadcast.global_reach", Map.of(
                    "player", player.getName(),
                    "show_item", showItem,
                    "star", star
            ));
            Bukkit.getOnlinePlayers().forEach(viewer -> plugin.messageService().sendRaw(viewer, message));
        }
    }

    private AttemptPreview ineligiblePreview(String errorKey, StrengthenState state) {
        return ineligiblePreview(errorKey, state, null);
    }

    private AttemptPreview ineligiblePreview(String errorKey, StrengthenState state, StrengthenRecipe recipe) {
        int currentStar = state == null ? 0 : state.currentStar();
        int temper = state == null ? 0 : state.temperLevel();
        return new AttemptPreview(false, errorKey, state, recipe, currentStar, currentStar, 0D, List.of(),
                currentStar, temper, false, 0, Map.of(), Set.of(), List.of(), List.of());
    }

    private StoredState readStoredState(ItemStack itemStack, ItemSource baseSource, String fallbackSignature) {
        if (itemAssemblyService == null || itemStack == null || !itemAssemblyService.isEmakiItem(itemStack)) {
            return StoredState.empty(baseSource, fallbackSignature);
        }
        EmakiItemLayerSnapshot snapshot = itemAssemblyService.readLayerSnapshot(itemStack, "strengthen");
        if (snapshot == null) {
            return StoredState.empty(baseSource, fallbackSignature);
        }
        Map<String, Object> audit = snapshot.audit();
        return new StoredState(
                true,
                Texts.toStringSafe(audit.get("recipe_id")),
                Numbers.tryParseInt(audit.get("current_star"), 0),
                Numbers.tryParseInt(audit.get("crack_level"), 0),
                parseFlagSet(audit.get("first_reach_flags")),
                Numbers.tryParseInt(audit.get("success_count"), 0),
                Numbers.tryParseInt(audit.get("failure_count"), 0),
                Numbers.tryParseLong(audit.get("last_attempt_at"), 0L),
                Texts.toStringSafe(audit.get("materials_signature")),
                Texts.isBlank(Texts.toStringSafe(audit.get("base_source_signature")))
                        ? fallbackSignature
                        : Texts.toStringSafe(audit.get("base_source_signature"))
        );
    }

    private Set<Integer> parseFlagSet(Object raw) {
        Set<Integer> flags = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Integer value = Numbers.tryParseInt(entry, null);
                if (value != null) {
                    flags.add(value);
                }
            }
        } else if (raw != null) {
            Integer value = Numbers.tryParseInt(raw, null);
            if (value != null) {
                flags.add(value);
            }
        }
        return flags;
    }

    private MaterialPlan resolveMaterialPlan(Player player, AttemptContext context, StrengthenRecipe.StarStage stage) {
        if (stage == null) {
            return new MaterialPlan("strengthen.error.material_missing", List.of(), List.of(), false, 0);
        }
        List<AttemptMaterial> requiredMaterials = new ArrayList<>();
        for (StrengthenRecipe.StarStageMaterial material : stage.materials()) {
            if (material == null || material.optional()) {
                continue;
            }
            int available = player == null ? 0 : countInventoryMaterial(player, material.item());
            AttemptMaterial attemptMaterial = new AttemptMaterial(
                    material.item(),
                    material.amount(),
                    available,
                    false,
                    material.protection(),
                    material.temperBoost(),
                    Math.max(0, material.amount())
            );
            requiredMaterials.add(attemptMaterial);
            if (!attemptMaterial.satisfied()) {
                return new MaterialPlan("strengthen.error.material_missing", requiredMaterials, buildEmptyOptionalMaterials(context), false, 0);
            }
        }

        Map<String, StrengthenRecipe.StarStageMaterial> optionalByItem = new LinkedHashMap<>();
        for (StrengthenRecipe.StarStageMaterial material : stage.materials()) {
            if (material != null && material.optional()) {
                optionalByItem.putIfAbsent(Texts.lower(material.item()), material);
            }
        }

        List<ItemStack> inputs = context == null ? List.of() : context.materialInputs();
        List<AttemptMaterial> optionalMaterials = new ArrayList<>();
        boolean protectionApplied = false;
        int temperBonus = 0;
        for (ItemStack input : inputs) {
            if (input == null || input.getType().isAir()) {
                optionalMaterials.add(new AttemptMaterial("", 0, 0, true, false, 0, 0));
                continue;
            }
            String token = resolveItemToken(input);
            StrengthenRecipe.StarStageMaterial matched = optionalByItem.get(Texts.lower(token));
            if (matched == null) {
                return new MaterialPlan("strengthen.error.invalid_optional_material", requiredMaterials, optionalMaterials, false, 0);
            }
            int available = input.getAmount();
            int consumed = resolveOptionalConsumeAmount(matched, available);
            optionalMaterials.add(new AttemptMaterial(
                    matched.item(),
                    matched.amount(),
                    available,
                    true,
                    matched.protection(),
                    matched.temperBoost(),
                    consumed
            ));
            protectionApplied = protectionApplied || (matched.protection() && available > 0);
            temperBonus += consumed * matched.temperBoost();
        }

        return new MaterialPlan("", List.copyOf(requiredMaterials), List.copyOf(optionalMaterials), protectionApplied, temperBonus);
    }

    private List<AttemptMaterial> buildEmptyOptionalMaterials(AttemptContext context) {
        List<ItemStack> inputs = context == null ? List.of() : context.materialInputs();
        List<AttemptMaterial> result = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            result.add(new AttemptMaterial("", 0, 0, true, false, 0, 0));
        }
        return List.copyOf(result);
    }

    private int resolveOptionalConsumeAmount(StrengthenRecipe.StarStageMaterial material, int available) {
        if (material == null || available <= 0) {
            return 0;
        }
        if (material.temperBoost() > 0) {
            return material.amount() > 0 ? Math.min(material.amount(), available) : available;
        }
        if (material.protection()) {
            return 1;
        }
        if (material.amount() > 0) {
            return Math.min(material.amount(), available);
        }
        return available;
    }

    private int countInventoryMaterial(Player player, String itemToken) {
        return (int) InventoryItemUtil.countItems(player, itemSourceService, itemToken);
    }

    private boolean consumeRequiredInventoryMaterials(Player player, List<AttemptMaterial> materials) {
        if (player == null || materials == null || materials.isEmpty()) {
            return true;
        }
        for (AttemptMaterial material : materials) {
            if (material == null || material.consumedAmount() <= 0) {
                continue;
            }
            if (countInventoryMaterial(player, material.item()) < material.consumedAmount()) {
                return false;
            }
        }
        for (AttemptMaterial material : materials) {
            if (material == null || material.consumedAmount() <= 0) {
                continue;
            }
            if (!removeInventoryMaterial(player.getInventory(), material.item(), material.consumedAmount())) {
                return false;
            }
        }
        return true;
    }

    private boolean removeInventoryMaterial(PlayerInventory inventory, String itemToken, int amount) {
        return InventoryItemUtil.removeItems(inventory, itemSourceService, itemToken, amount);
    }

    private void refundRequiredInventoryMaterials(Player player, List<AttemptMaterial> materials) {
        if (player == null || materials == null || materials.isEmpty()) {
            return;
        }
        for (AttemptMaterial material : materials) {
            if (material == null || Texts.isBlank(material.item()) || material.consumedAmount() <= 0) {
                continue;
            }
            ItemSource source = ItemSourceUtil.parse(material.item());
            ItemStack restored = source == null ? null : plugin.coreItemFactory().create(source, material.consumedAmount());
            if (restored == null) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(restored);
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private ItemStack rebuildWithState(ItemStack itemStack, StrengthenState state, String materialsSignature) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(state.recipeId());
        if (recipe == null) {
            return null;
        }
        if (itemAssemblyService == null) {
            return null;
        }
        EmakiItemLayerSnapshot snapshot = snapshotBuilder.buildLayerSnapshot(recipe, state, materialsSignature);
        ItemStack rebuilt = itemAssemblyService.preview(new EmakiItemAssemblyRequest(
                state.baseSource(),
                Math.max(1, itemStack.getAmount()),
                itemStack,
                List.of(snapshot)
        ));
        if (rebuilt != null) {
            rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
            applyPdcAttributes(rebuilt, recipe, state);
        }
        return rebuilt;
    }

    private String buildMaterialsSignature(AttemptPreview preview) {
        List<Object> signatureData = new ArrayList<>();
        if (preview != null && preview.optionalMaterials() != null) {
            for (AttemptMaterial material : preview.optionalMaterials()) {
                if (material == null || Texts.isBlank(material.item()) || material.consumedAmount() <= 0) {
                    continue;
                }
                signatureData.add(Map.of("item", material.item(), "amount", material.consumedAmount()));
            }
        }
        return SignatureUtil.stableSignature(signatureData);
    }

    private Map<String, Object> replacements(AttemptPreview preview, int star) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("star", star);
        replacements.put("temper", preview == null ? 0 : preview.failureTemper());
        if (preview != null && preview.recipe() != null) {
            replacements.put("recipe", preview.recipe().displayName());
        }
        if (preview != null && !preview.costs().isEmpty()) {
            AttemptCost first = preview.costs().get(0);
            replacements.put("cost", first.amount());
            replacements.put("currency", first.displayName());
            replacements.put("costs", renderCosts(preview.costs()));
        } else {
            replacements.put("cost", 0);
            replacements.put("currency", freeCostLabel());
            replacements.put("costs", freeCostLabel());
        }
        return replacements;
    }

    private String renderCosts(List<AttemptCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return freeCostLabel();
        }
        List<String> parts = new ArrayList<>();
        for (AttemptCost cost : costs) {
            parts.add(cost.amount() + " " + cost.displayName());
        }
        return String.join(", ", parts);
    }

    private String freeCostLabel() {
        var message = plugin.messageService() == null
                ? ""
                : plugin.messageService().message("strengthen.misc.free_cost");
        return Texts.isBlank(message) ? "免费" : message;
    }

    private Set<Integer> collectFirstReach(Set<Integer> currentFlags, int targetStar) {
        if (targetStar <= 0 || currentFlags != null && currentFlags.contains(targetStar)) {
            return Set.of();
        }
        return Set.of(targetStar);
    }

    private String resolveItemToken(ItemStack itemStack) {
        ItemSource source = recipeResolver.resolveBaseSource(itemStack);
        return source == null ? "" : ItemSourceUtil.toShorthand(source);
    }

    private void applyPdcAttributes(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state) {
        if (itemStack == null || recipe == null || state == null) {
            return;
        }
        ReflectivePdcAttributeGateway gateway = plugin.pdcAttributeGateway();
        if (gateway == null || !gateway.available()) {
            return;
        }
        Map<String, Double> eaAttributes = recipe.cumulativeEaAttributes(state.currentStar());
        if (eaAttributes.isEmpty()) {
            gateway.clear(itemStack, PDC_ATTRIBUTE_SOURCE_ID);
            return;
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("recipe_id", recipe.id());
        meta.put("current_star", String.valueOf(state.currentStar()));
        gateway.write(itemStack, PDC_ATTRIBUTE_SOURCE_ID, eaAttributes, meta);
    }

    static StarProgress collectStarProgress(Set<Integer> currentFlags, Set<Integer> reachedNow) {
        Set<Integer> updated = new LinkedHashSet<>(currentFlags == null ? Set.of() : currentFlags);
        Set<Integer> newlyReached = new LinkedHashSet<>();
        if (reachedNow != null) {
            for (Integer stage : reachedNow) {
                if (stage != null && updated.add(stage)) {
                    newlyReached.add(stage);
                }
            }
        }
        return new StarProgress(Set.copyOf(updated), Set.copyOf(newlyReached));
    }

    private record MaterialPlan(String errorKey,
            List<AttemptMaterial> requiredMaterials,
            List<AttemptMaterial> optionalMaterials,
            boolean protectionApplied,
            int appliedTemperBonus) {

    }

    private record StoredState(boolean hasLayer,
            String recipeId,
            int currentStar,
            int crackLevel,
            Set<Integer> firstReachFlags,
            int successCount,
            int failureCount,
            long lastAttemptAt,
            String materialsSignature,
            String baseSourceSignature) {

        private static StoredState empty(ItemSource baseSource, String fallbackSignature) {
            String signature = Texts.isBlank(fallbackSignature) ? ItemSourceUtil.toShorthand(baseSource) : fallbackSignature;
            return new StoredState(false, "", 0, 0, Set.of(), 0, 0, 0L, "", signature);
        }

        private StoredState withBaseSourceSignature(String fallbackSignature) {
            String resolvedSignature = Texts.isBlank(baseSourceSignature) ? fallbackSignature : baseSourceSignature;
            if (java.util.Objects.equals(baseSourceSignature, resolvedSignature)) {
                return this;
            }
            return new StoredState(
                    hasLayer,
                    recipeId,
                    currentStar,
                    crackLevel,
                    firstReachFlags,
                    successCount,
                    failureCount,
                    lastAttemptAt,
                    materialsSignature,
                    resolvedSignature
            );
        }
    }

    private record ResolvedState(StrengthenState state, StoredState stored) {

    }

    record StarProgress(Set<Integer> updatedFlags, Set<Integer> newlyReached) {

    }
}
