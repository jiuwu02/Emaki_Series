package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class ForgeItemRefreshService {

    private final EmakiForgePlugin plugin;
    private final ForgeLayerSnapshotBuilder snapshotBuilder;
    private final ForgeQualityModifierResolver qualityModifierResolver = new ForgeQualityModifierResolver();
    private final Set<String> warningCache = new LinkedHashSet<>();

    public ForgeItemRefreshService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.snapshotBuilder = new ForgeLayerSnapshotBuilder(plugin);
    }

    public void refreshOnlinePlayers() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::refreshOnlinePlayers);
            return;
        }
        synchronized (warningCache) {
            warningCache.clear();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayerInventory(player);
        }
    }

    public void refreshPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean storageChanged = refreshArray(storage);
        if (storageChanged) {
            inventory.setStorageContents(storage);
        }
        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = refreshArray(armor);
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        ItemStack refreshedOffHand = refreshItem(offHand);
        if (refreshedOffHand != offHand) {
            inventory.setItemInOffHand(refreshedOffHand);
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack refreshedCursor = refreshItem(cursor);
        if (refreshedCursor != cursor) {
            player.setItemOnCursor(refreshedCursor);
        }
    }

    public void refreshDroppedItem(Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid()) {
            return;
        }
        ItemStack refreshed = refreshItem(itemEntity.getItemStack());
        if (refreshed != itemEntity.getItemStack()) {
            itemEntity.setItemStack(refreshed);
        }
    }

    public ItemStack refreshItem(ItemStack itemStack) {
        RefreshPlan plan = buildRefreshPlan(itemStack);
        if (plan == null || !plan.shouldRefresh()) {
            return itemStack;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null) {
            return itemStack;
        }
        EmakiItemLayerSnapshot snapshot = snapshotBuilder.buildLayerSnapshot(
                plan.recipe(),
                plan.materials(),
                plan.multiplier(),
                plan.qualityTier(),
                plan.forgedAt()
        );
        ItemStack rebuilt = coreLib.itemAssemblyService().preview(new EmakiItemAssemblyRequest(null, 0, itemStack, List.of(snapshot)));
        if (rebuilt == null) {
            warnOnce(
                    "refresh_failed|" + plan.recipe().id() + "|" + plan.signature(),
                    "console.forge_refresh_failed",
                    Map.of("recipe", plan.recipe().id())
            );
            return itemStack;
        }
        rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
        return rebuilt;
    }

    private boolean refreshArray(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < items.length; index++) {
            ItemStack original = items[index];
            ItemStack refreshed = refreshItem(original);
            if (refreshed != original) {
                items[index] = refreshed;
                changed = true;
            }
        }
        return changed;
    }

    private RefreshPlan buildRefreshPlan(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || !coreLib.itemAssemblyService().isEmakiItem(itemStack)) {
            return null;
        }
        EmakiItemLayerSnapshot oldSnapshot = coreLib.itemAssemblyService().readLayerSnapshot(itemStack, "forge");
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
            String materialId = ConfigNodes.string(entry, "material_id", null);
            if (Texts.isBlank(materialId)) {
                warnOnce(
                        "invalid_material_entry|" + recipeId + "|" + snapshotId + "|" + fallbackSequence,
                        "console.forge_refresh_invalid_audit",
                        Map.of("reason", "missing material_id")
                );
                return null;
            }
            ForgeMaterial material = plugin.forgeService().findMaterialById(materialId);
            if (material == null) {
                warnOnce(
                        "missing_material|" + recipeId + "|" + materialId + "|" + snapshotId,
                        "console.forge_refresh_missing_material",
                        Map.of("recipe", recipeId, "material", materialId)
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
        return Texts.isBlank(signature) ? "legacy" : signature;
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

    private record RefreshPlan(boolean shouldRefresh,
            Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            long forgedAt,
            String signature) {

    }
}
