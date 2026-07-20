package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import emaki.jiuwu.craft.corelib.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.item.PlayerItemRefreshService;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
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
        synchronized (warningCache) {
            warningCache.clear();
        }
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(new RefreshSummary(0, 0));
        }
        List<CompletableFuture<Boolean>> refreshes = new ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<Boolean> refresh = new CompletableFuture<>();
            refreshes.add(refresh);
            try {
                var scheduled = executionDispatcher.runEntity(
                        plugin,
                        player,
                        () -> {
                            try {
                                if (!player.isOnline()) {
                                    refresh.complete(false);
                                    return;
                                }
                                refreshPlayerInventory(player);
                                refresh.complete(true);
                            } catch (Throwable throwable) {
                                refresh.completeExceptionally(throwable);
                            }
                        },
                        () -> refresh.complete(false));
                if (scheduled == null) {
                    refresh.completeExceptionally(new RejectedExecutionException(
                            "Forge player refresh scheduling was rejected for " + player.getUniqueId()));
                }
            } catch (Throwable throwable) {
                refresh.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(refreshes.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> new RefreshSummary(
                        players.size(),
                        (int) refreshes.stream().filter(future -> Boolean.TRUE.equals(future.join())).count()));
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
        ItemStack refreshedOffHand = refreshItem(player, "offhand", offHand);
        if (refreshedOffHand != offHand) {
            inventory.setItemInOffHand(refreshedOffHand);
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack refreshedCursor = refreshItem(player, "cursor", cursor);
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
            return itemStack;
        }
        debugForgeRefresh(player, target, "assembly_output", plan, rebuilt);
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
            ItemStack refreshed = refreshItem(player, targetPrefix + ":" + index, original);
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
            ItemStack refreshed = refreshItem(player, target, original);
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
        debugLogger.logRaw("forge", player, "[DEBUG:FORGE_REFRESH]"
                + " phase=" + Texts.toStringSafe(phase)
                + " target=" + Texts.toStringSafe(target)
                + " recipe=" + (plan == null || plan.recipe() == null ? "-" : plan.recipe().id())
                + " materials_signature=" + shortValue(plan == null ? "" : plan.signature())
                + " item=" + itemStateSummary(itemStack));
    }

    private String itemStateSummary(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "empty";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<ItemOperationEntry> entries = operationLedger.readAll(itemStack);
        int loreSize = itemMeta == null ? 0 : ItemTextBridge.loreLines(itemMeta).size();
        return "type=" + itemStack.getType()
                + " lore=" + loreSize
                + " set_signature=" + shortValue(pdcString(itemMeta, "set_signature"))
                + " set_lore_lines=" + Objects.toString(pdcInteger(itemMeta, "set_lore_lines"), "-")
                + " operations=" + entries.stream()
                        .filter(Objects::nonNull)
                        .map(entry -> entry.sourceNamespace() + ":" + entry.operationId())
                        .toList();
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

    private String shortValue(String value) {
        String safe = Texts.toStringSafe(value);
        if (safe.isBlank()) {
            return "-";
        }
        return safe.length() <= 16 ? safe : safe.substring(0, 16);
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
        java.util.Map<String, Object> variables = buildOperationVariables(plan);

        String operationId = "forge:" + recipe.id();
        Object nameActionsToApply = allNameActions.size() == 1 ? allNameActions.get(0) : allNameActions;
        Object loreActionsToApply = allLoreActions.size() == 1 ? allLoreActions.get(0) : allLoreActions;
        operationLedger.apply(itemStack, operationId, "forge",
                allNameActions.isEmpty() ? null : nameActionsToApply,
                allLoreActions.isEmpty() ? null : loreActionsToApply,
                variables);
    }

    private java.util.Map<String, Object> buildOperationVariables(RefreshPlan plan) {
        double multiplier = plan == null ? 1D : plan.multiplier();
        java.util.Map<String, Object> variables = new java.util.LinkedHashMap<>(snapshotBuilder.buildDisplayVariables(
                plan == null ? java.util.List.of() : plan.materials(),
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

    public record RefreshSummary(int players, int refreshed) {
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
