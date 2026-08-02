package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeFingerprintService {

    private final EmakiForgePlugin plugin;

    ForgeFingerprintService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    String buildPreviewFingerprint(Player player, Recipe recipe, GuiItems guiItems) {
        List<Object> parts = new ArrayList<>();
        parts.add(player == null ? "" : player.getUniqueId().toString());
        parts.add(recipe == null ? "" : recipe.id());
        parts.add(player == null || recipe == null ? 0 : plugin.playerDataStore().guaranteeCounter(player.getUniqueId(), recipe.id()));
        if (recipe != null && recipe.requiresTargetInput()) {
            appendItemSignature(parts, "target", guiItems == null ? null : guiItems.targetItem());
        }
        appendMappedSignatures(parts, "blueprint", guiItems == null ? null : guiItems.blueprints());
        appendMappedSignatures(parts, "required", guiItems == null ? null : guiItems.requiredMaterials());
        appendMappedSignatures(parts, "optional", guiItems == null ? null : guiItems.optionalMaterials());
        return SignatureUtil.stableSignature(parts);
    }

    String buildRollKey(String fingerprint, long previewSeed) {
        return SignatureUtil.combine(fingerprint, Long.toUnsignedString(previewSeed));
    }

    String buildPreparationCacheKey(Player player,
            Recipe recipe,
            GuiItems guiItems,
            long previewSeed,
            long forgedAt) {
        return SignatureUtil.stableSignature(List.of(
                buildPreviewFingerprint(player, recipe, guiItems),
                Long.toUnsignedString(previewSeed),
                Long.toUnsignedString(forgedAt)
        ));
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
        ItemSourceRef source = plugin.itemIdentifierService().identifyItem(itemStack);
        parts.add(prefix);
        parts.add(source == null ? "" : ItemSourceUtil.toShorthand(source));
        parts.add(itemStack.getAmount());
    }
}
