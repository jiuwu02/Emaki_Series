package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.item.PlayerItemRefreshService;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class ForgeItemRefreshService implements PlayerItemRefreshService {

    private final EmakiForgePlugin plugin;
    private final EmakiItemAssemblyService itemAssemblyService;
    private final ExecutionDispatcher executionDispatcher;
    private final ForgeLayerSnapshotBuilder snapshotBuilder;
    private final ForgePdcAttributeWriter pdcAttributeWriter;
    private final ForgeQualityModifierResolver qualityModifierResolver = new ForgeQualityModifierResolver();
    private final ItemOperationLedger operationLedger;
    private final Set<String> warningCache = new LinkedHashSet<>();

    public ForgeItemRefreshService(EmakiForgePlugin plugin,
                                   EmakiItemAssemblyService itemAssemblyService,
                                   ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.itemAssemblyService = itemAssemblyService;
        this.executionDispatcher = executionDispatcher;
        this.snapshotBuilder = new ForgeLayerSnapshotBuilder(plugin);
        this.pdcAttributeWriter = new ForgePdcAttributeWriter(plugin);
        this.operationLedger = new ItemOperationLedger(plugin::debugLogger);
    }

    public CompletableFuture<RefreshSummary> refreshOnlinePlayers() {
        return refreshOnlinePlayers(plugin.runtimeGeneration());
    }

    public CompletableFuture<RefreshSummary> refreshOnlinePlayers(long generation) {
        long started = System.nanoTime();
        synchronized (warningCache) {
            warningCache.clear();
        }
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(new RefreshSummary(generation, 0, 0, 0, 0,
                    !plugin.isGenerationActive(generation), System.nanoTime() - started));
        }
        AtomicInteger refreshed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<CompletableFuture<Void>> refreshes = new ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<Void> refresh = new CompletableFuture<>();
            refreshes.add(refresh);
            try {
                var scheduled = executionDispatcher.runEntity(
                        plugin,
                        player,
                        () -> {
                            try {
                                if (!player.isOnline() || !plugin.isGenerationActive(generation)) {
                                    skipped.incrementAndGet();
                                    refresh.complete(null);
                                    return;
                                }
                                refreshPlayerInventory(player);
                                refreshed.incrementAndGet();
                                refresh.complete(null);
                            } catch (Throwable throwable) {
                                failed.incrementAndGet();
                                refresh.completeExceptionally(throwable);
                            }
                        },
                        () -> {
                            skipped.incrementAndGet();
                            refresh.complete(null);
                        });
                if (scheduled == null) {
                    failed.incrementAndGet();
                    refresh.completeExceptionally(new RejectedExecutionException(
                            "Forge player refresh scheduling was rejected."));
                }
            } catch (Throwable throwable) {
                failed.incrementAndGet();
                refresh.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(refreshes.toArray(CompletableFuture[]::new))
                .handle((ignored, throwable) -> new RefreshSummary(
                        generation,
                        players.size(),
                        refreshed.get(),
                        skipped.get(),
                        failed.get(),
                        !plugin.isGenerationActive(generation),
                        System.nanoTime() - started));
    }

    @Override
    public void refreshPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean storageChanged = refreshArray(player, "storage", storage);
        if (storageChanged) {
            inventory.setStorageContents(storage);
        }
        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = refreshArmor(player, armor);
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        ItemStack refreshedOffHand = refreshItem(player, "offhand", offHand, true);
        if (refreshedOffHand != offHand) {
            inventory.setItemInOffHand(refreshedOffHand);
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack refreshedCursor = refreshItem(player, "cursor", cursor, true);
        if (refreshedCursor != cursor) {
            player.setItemOnCursor(refreshedCursor);
        }
    }

    @Override
    public void refreshDroppedItem(Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid()) {
            return;
        }
        ItemStack refreshed = refreshItem(null, "dropped", itemEntity.getItemStack());
        if (refreshed != itemEntity.getItemStack()) {
            itemEntity.setItemStack(refreshed);
        }
    }

    public ItemStack refreshItem(ItemStack itemStack) {
        return refreshItem(null, "direct", itemStack);
    }

    private ItemStack refreshItem(Player player, String target, ItemStack itemStack) {
        return refreshItem(player, target, itemStack, false);
    }

    private ItemStack refreshItem(Player player, String target, ItemStack itemStack, boolean failOnRefreshError) {
        RefreshPlan plan = buildRefreshPlan(itemStack);
        if (plan == null || !plan.shouldRefresh()) {
            return itemStack;
        }
        if (itemAssemblyService == null) {
            return itemStack;
        }
        debugForgeRefresh(player, target, "input", plan, itemStack);
        EmakiItemLayerSnapshot snapshot = snapshotBuilder.buildLayerSnapshot(
                plan.recipe(),
                plan.materials(),
                plan.multiplier(),
                plan.qualityTier(),
                plan.forgedAt(),
                null
        );
        EmakiItemAssemblyRequest request = new EmakiItemAssemblyRequest(
                null,
                0,
                itemStack,
                List.of(snapshot),
                player == null ? null : player.getUniqueId()
        );
        ItemStack rebuilt = itemAssemblyService.preview(request, target, plugin.debugLogger());
        if (rebuilt == null) {
            debugForgeRefresh(player, target, "assembly_failed", plan, null);
            warnOnce(
                    "refresh_failed|" + plan.recipe().id() + "|" + plan.signature(),
                    "console.forge_refresh_failed",
                    Map.of("recipe", plan.recipe().id())
            );
            if (failOnRefreshError) {
                throw new IllegalStateException("Forge item assembly failed for recipe '" + plan.recipe().id() + "'.");
            }
            return itemStack;
        }
        debugForgeRefresh(player, target, "assembly_output", plan, rebuilt);
        StateLoss stateLoss = detectStateLoss(itemStack, rebuilt);
        if (stateLoss.detected()) {
            debugForgeStateLoss(player, target, plan, stateLoss, itemStack, rebuilt);
            if (failOnRefreshError) {
                throw new IllegalStateException("Forge item refresh would discard persistent state for recipe '"
                        + plan.recipe().id() + "'.");
            }
            return itemStack;
        }
        rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
        pdcAttributeWriter.apply(plan.recipe(), plan.materials(), plan.multiplier(), plan.qualityTier(), rebuilt);
        applyRefreshOperations(rebuilt, plan);
        debugForgeRefresh(player, target, "output", plan, rebuilt);
        return rebuilt;
    }

    private boolean refreshArray(Player player, String targetPrefix, ItemStack[] items) {
        if (items == null || items.length == 0) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < items.length; index++) {
            ItemStack original = items[index];
            ItemStack refreshed = refreshItem(player, targetPrefix + ":" + index, original, true);
            if (refreshed != original) {
                items[index] = refreshed;
                changed = true;
            }
        }
        return changed;
    }

    private boolean refreshArmor(Player player, ItemStack[] armor) {
        if (armor == null || armor.length == 0) {
            return false;
        }
        String[] slots = {"feet", "legs", "chest", "head"};
        boolean changed = false;
        for (int index = 0; index < armor.length; index++) {
            ItemStack original = armor[index];
            String target = "armor:" + (index < slots.length ? slots[index] : index);
            ItemStack refreshed = refreshItem(player, target, original, true);
            if (refreshed != original) {
                armor[index] = refreshed;
                changed = true;
            }
        }
        return changed;
    }

    private RefreshPlan buildRefreshPlan(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        if (itemAssemblyService == null || !itemAssemblyService.isEmakiItem(itemStack)) {
            return null;
        }
        EmakiItemLayerSnapshot oldSnapshot = itemAssemblyService.readLayerSnapshot(itemStack, "forge");
        if (oldSnapshot == null) {
            return null;
        }
        Map<String, Object> audit = oldSnapshot.audit();
        String recipeId = Texts.toStringSafe(audit.get("recipe_id"));
        if (Texts.isBlank(recipeId)) {
            warnOnce(
                    "missing_recipe_id|" + snapshotIdentity(audit),
                    "console.forge_refresh_invalid_audit",
                    Map.of("reason", "missing recipe_id")
            );
            return null;
        }
        Recipe recipe = plugin.recipeLoader().all().get(recipeId);
        if (recipe == null) {
            warnOnce(
                    "missing_recipe|" + recipeId + "|" + snapshotIdentity(audit),
                    "console.forge_refresh_missing_recipe",
                    Map.of("recipe", recipeId)
            );
            return null;
        }
        QualitySettings settings = plugin.appConfig().qualitySettings();
        String storedQuality = Texts.toStringSafe(audit.get("quality"));
        QualitySettings.QualityTier storedTier = settings.findTier(storedQuality);
        if (storedTier == null) {
            warnOnce(
                    "invalid_quality|" + recipeId + "|" + storedQuality + "|" + snapshotIdentity(audit),
                    "console.forge_refresh_invalid_quality",
                    Map.of("recipe", recipeId, "quality", storedQuality)
            );
            return null;
        }
        List<ForgeMaterialContribution> materials = resolveAuditMaterials(recipeId, audit.get("materials"), snapshotIdentity(audit));
        if (materials == null) {
            return null;
        }
        String signature = snapshotBuilder.buildMaterialsSignature(materials);
        String oldSignature = Texts.toStringSafe(audit.get("materials_signature"));
        boolean shouldRefresh = Texts.isBlank(oldSignature) || !oldSignature.equals(signature);
        if (!shouldRefresh) {
            return new RefreshPlan(false, recipe, materials, storedTier, storedTier.multiplier(), readForgedAt(audit), signature);
        }
        QualitySettings.QualityTier refreshedTier = qualityModifierResolver.applyModifiers(
                settings,
                storedTier,
                snapshotBuilder.collectQualityModifiers(materials)
        );
        return new RefreshPlan(true, recipe, materials, refreshedTier, refreshedTier.multiplier(), readForgedAt(audit), signature);
    }

    private List<ForgeMaterialContribution> resolveAuditMaterials(String recipeId, Object rawMaterials, String snapshotId) {
        List<ForgeMaterialContribution> result = new ArrayList<>();
        int fallbackSequence = 0;
        for (Object rawEntry : ConfigNodes.asObjectList(rawMaterials)) {
            Map<String, Object> entry = ConfigNodes.entries(rawEntry);
            String materialItem = ConfigNodes.string(entry, "material_item", null);
            if (Texts.isBlank(materialItem)) {
                warnOnce(
                        "invalid_material_entry|" + recipeId + "|" + snapshotId + "|" + fallbackSequence,
                        "console.forge_refresh_invalid_audit",
                        Map.of("reason", "missing material_item")
                );
                return null;
            }
            Recipe recipe = plugin.recipeLoader().all().get(recipeId);
            ForgeMaterial material = recipe == null ? null : recipe.findMaterialByItem(materialItem);
            if (material == null) {
                warnOnce(
                        "missing_material|" + recipeId + "|" + materialItem + "|" + snapshotId,
                        "console.forge_refresh_missing_material",
                        Map.of("recipe", recipeId, "material", materialItem)
                );
                return null;
            }
            int amount = Numbers.tryParseInt(entry.get("amount"), 0);
            if (amount <= 0) {
                fallbackSequence++;
                continue;
            }
            int sequence = Numbers.tryParseInt(entry.get("sequence"), fallbackSequence);
            result.add(new ForgeMaterialContribution(
                    material,
                    amount,
                    Numbers.tryParseInt(entry.get("slot"), -1),
                    ConfigNodes.string(entry, "category", ""),
                    sequence,
                    material.source()
            ));
            fallbackSequence = Math.max(fallbackSequence + 1, sequence + 1);
        }
        return result;
    }

    private long readForgedAt(Map<String, Object> audit) {
        Long forgedAt = Numbers.tryParseLong(audit == null ? null : audit.get("forged_at"), null);
        return forgedAt == null || forgedAt <= 0L ? System.currentTimeMillis() : forgedAt;
    }

    private String snapshotIdentity(Map<String, Object> audit) {
        String signature = audit == null ? "" : Texts.toStringSafe(audit.get("materials_signature"));
        return Texts.isBlank(signature) ? "unknown" : signature;
    }

    private void debugForgeRefresh(Player player, String target, String phase, RefreshPlan plan, ItemStack itemStack) {
        DebugLogger debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("forge", player)) {
            return;
        }
        Map<String, Object> replacements = debugReplacements(
                "phase", Texts.toStringSafe(phase),
                "target", Texts.toStringSafe(target),
                "recipe", plan == null || plan.recipe() == null ? "" : plan.recipe().id(),
                "materials_signature", plan == null ? "" : plan.signature()
        );
        putItemState(replacements, "item", itemStack);
        debugLogger.log("forge", player, "forge.refresh", replacements);
    }

    private StateLoss detectStateLoss(ItemStack original, ItemStack rebuilt) {
        if (original == null || rebuilt == null) {
            return new StateLoss(true, List.of("item"), operationIds(original));
        }
        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta rebuiltMeta = rebuilt.getItemMeta();
        Set<NamespacedKey> originalKeys = originalMeta == null
                ? Set.of()
                : originalMeta.getPersistentDataContainer().getKeys();
        Set<NamespacedKey> rebuiltKeys = rebuiltMeta == null
                ? Set.of()
                : rebuiltMeta.getPersistentDataContainer().getKeys();
        List<String> missingPdc = originalKeys.stream()
                .filter(key -> !rebuiltKeys.contains(key))
                .map(NamespacedKey::toString)
                .sorted()
                .toList();
        List<String> originalOperations = operationIds(original);
        Set<String> rebuiltOperations = new LinkedHashSet<>(operationIds(rebuilt));
        List<String> missingOperations = originalOperations.stream()
                .filter(operationId -> !rebuiltOperations.contains(operationId))
                .toList();
        return new StateLoss(!missingPdc.isEmpty() || !missingOperations.isEmpty(), missingPdc, missingOperations);
    }

    private void debugForgeStateLoss(Player player,
                                     String target,
                                     RefreshPlan plan,
                                     StateLoss stateLoss,
                                     ItemStack original,
                                     ItemStack rebuilt) {
        DebugLogger debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("forge", player)) {
            return;
        }
        Map<String, Object> replacements = debugReplacements(
                "target", Texts.toStringSafe(target),
                "recipe", plan == null || plan.recipe() == null ? "" : plan.recipe().id(),
                "materials_signature", plan == null ? "" : plan.signature(),
                "missing_pdc", stateLoss.missingPdc(),
                "missing_operations", stateLoss.missingOperations()
        );
        putItemState(replacements, "original", original);
        putItemState(replacements, "rebuilt", rebuilt);
        debugLogger.log("forge", player, "forge.state_loss", replacements);
    }

    private List<String> operationIds(ItemStack itemStack) {
        return operationLedger.readAll(itemStack).stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.sourceNamespace() + ":" + entry.operationId())
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, Object> debugReplacements(Object... entries) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            replacements.put(Texts.toStringSafe(entries[index]), entries[index + 1]);
        }
        return replacements;
    }

    private void putItemState(Map<String, Object> replacements, String prefix, ItemStack itemStack) {
        boolean empty = itemStack == null || itemStack.getType().isAir();
        replacements.put(prefix + "_empty", empty);
        if (empty) {
            replacements.put(prefix + "_type", "");
            replacements.put(prefix + "_lore_lines", 0);
            replacements.put(prefix + "_set_signature", "");
            replacements.put(prefix + "_set_lore_lines", "");
            replacements.put(prefix + "_operations", List.of());
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        replacements.put(prefix + "_type", itemStack.getType());
        replacements.put(prefix + "_lore_lines", itemMeta == null ? 0 : ItemTextBridge.loreLines(itemMeta).size());
        replacements.put(prefix + "_set_signature", pdcString(itemMeta, "set_signature"));
        replacements.put(prefix + "_set_lore_lines", Objects.toString(pdcInteger(itemMeta, "set_lore_lines"), ""));
        replacements.put(prefix + "_operations", operationIds(itemStack));
    }

    private String pdcString(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return "";
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (key.getKey().endsWith(field) && container.has(key, PersistentDataType.STRING)) {
                return Texts.toStringSafe(container.get(key, PersistentDataType.STRING));
            }
        }
        return "";
    }

    private Integer pdcInteger(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return null;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (key.getKey().endsWith(field) && container.has(key, PersistentDataType.INTEGER)) {
                return container.get(key, PersistentDataType.INTEGER);
            }
        }
        return null;
    }

    private void warnOnce(String cacheKey, String messageKey, Map<String, ?> replacements) {
        if (Texts.isBlank(cacheKey) || Texts.isBlank(messageKey)) {
            return;
        }
        synchronized (warningCache) {
            if (!warningCache.add(cacheKey)) {
                return;
            }
        }
        plugin.messageService().warning(messageKey, replacements);
    }

    private void applyRefreshOperations(ItemStack itemStack, RefreshPlan plan) {
        if (plan == null || plan.recipe() == null) {
            return;
        }
        Recipe recipe = plan.recipe();
        List<Object> allNameActions = new ArrayList<>();
        List<Object> allLoreActions = new ArrayList<>();

        if (recipe.result() != null) {
            if (recipe.result().nameModifications() != null && !recipe.result().nameModifications().isEmpty()) {
                allNameActions.add(recipe.result().nameModifications());
            }
            if (recipe.result().loreActions() != null && !recipe.result().loreActions().isEmpty()) {
                allLoreActions.add(recipe.result().loreActions());
            }
        }
        if (plan.materials() != null) {
            for (ForgeMaterialContribution material : plan.materials()) {
                if (material == null || material.material() == null) {
                    continue;
                }
                Object matNameActions = material.material().nameModifications();
                Object matLoreActions = material.material().loreActions();
                if (matNameActions != null) {
                    allNameActions.add(matNameActions);
                }
                if (matLoreActions != null) {
                    allLoreActions.add(matLoreActions);
                }
            }
        }
        QualitySettings settings = plugin.appConfig() == null || plugin.appConfig().qualitySettings() == null
                ? QualitySettings.defaults()
                : plugin.appConfig().qualitySettings();
        if (plan.qualityTier() != null && settings.itemMetaEnabled()) {
            Object qualityNameActions = settings.itemMetaNameActions(plan.qualityTier().name());
            Object qualityLoreActions = settings.itemMetaLoreActions(plan.qualityTier().name());
            if (qualityNameActions != null) {
                allNameActions.add(qualityNameActions);
            }
            if (qualityLoreActions != null) {
                allLoreActions.add(qualityLoreActions);
            }
        }
        if (allNameActions.isEmpty() && allLoreActions.isEmpty()) {
            return;
        }
        Map<String, Object> variables = buildOperationVariables(plan);

        String operationId = "forge:" + recipe.id();
        Object nameActionsToApply = allNameActions.size() == 1 ? allNameActions.get(0) : allNameActions;
        Object loreActionsToApply = allLoreActions.size() == 1 ? allLoreActions.get(0) : allLoreActions;
        operationLedger.apply(itemStack, operationId, "forge",
                allNameActions.isEmpty() ? null : nameActionsToApply,
                allLoreActions.isEmpty() ? null : loreActionsToApply,
                variables);
    }

    private Map<String, Object> buildOperationVariables(RefreshPlan plan) {
        double multiplier = plan == null ? 1D : plan.multiplier();
        Map<String, Object> variables = new LinkedHashMap<>(snapshotBuilder.buildDisplayVariables(
                plan == null ? List.of() : plan.materials(),
                multiplier,
                plugin.appConfig().defaultNumberFormat()
        ));
        if (plan != null && plan.qualityTier() != null) {
            variables.put("quality", plan.qualityTier().name());
            variables.put("quality_name", plan.qualityTier().name());
        }
        variables.put("quality_multiplier", Numbers.formatNumber(multiplier, "0.##"));
        variables.put("multiplier", Numbers.formatNumber(multiplier, "0.##"));
        return variables;
    }

    public record RefreshSummary(long generation,
                                 int players,
                                 int refreshed,
                                 int skipped,
                                 int failed,
                                 boolean stale,
                                 long durationNanos) {

        public static RefreshSummary empty() {
            return new RefreshSummary(0L, 0, 0, 0, 0, false, 0L);
        }
    }

    private record StateLoss(boolean detected, List<String> missingPdc, List<String> missingOperations) {
    }

    private record RefreshPlan(boolean shouldRefresh,
                               Recipe recipe,
                               List<ForgeMaterialContribution> materials,
                               QualitySettings.QualityTier qualityTier,
                               double multiplier,
                               long forgedAt,
                               String signature) {

    }
}
