package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplaySpec;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;

final class WokRuntimeServiceDelegate2 {

    private final WokRuntimeService ownerRef;

    WokRuntimeServiceDelegate2(WokRuntimeService ownerRef) {
        this.ownerRef = ownerRef;
    }

    List<WokRuntimeService.WokIngredientDisplay> displayIngredients(WokRuntimeService.WokState state) {
        Map<String, WokRuntimeService.WokIngredientDisplay> grouped = new LinkedHashMap<>();
        for (WokRuntimeService.WokIngredientState ingredient : state.ingredients()) {
            String key = ownerRef.displayGroupKey(ingredient.source());
            ItemStack itemStack = ownerRef.displayItem(ingredient);
            WokRuntimeService.WokIngredientDisplay existing = grouped.get(key);
            if (existing == null || !ownerRef.isDisplayable(existing.itemStack())) {
                grouped.put(key, new WokRuntimeService.WokIngredientDisplay(ingredient.source(), itemStack));
            }
        }
        return grouped.values().stream()
                .filter(ingredient -> ownerRef.isDisplayable(ingredient.itemStack()))
                .toList();
    }

    String displayGroupKey(String source) {
        ItemSourceRef parsed = ItemSourceUtil.parse(source);
        String shorthand = parsed == null ? source : ItemSourceUtil.toShorthand(parsed);
        return Texts.normalizeId(shorthand);
    }

    ItemStack displayItem(WokRuntimeService.WokIngredientState ingredient) {
        for (Map<String, Object> itemData : ingredient.itemData()) {
            ItemStack storedItem = StoredItemCodec.deserialize(itemData);
            if (ownerRef.isDisplayable(storedItem)) {
                storedItem.setAmount(1);
                return storedItem;
            }
        }
        ItemSourceRef source = ItemSourceUtil.parse(ingredient.source());
        ItemStack itemStack = source == null ? null : ownerRef.itemSourceService.createItem(source, 1);
        if (ownerRef.isDisplayable(itemStack)) {
            itemStack.setAmount(1);
        }
        return itemStack;
    }

    boolean isDisplayable(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir();
    }

    CookingSettingsService.Vector3 layoutOffset(int index, int count) {
        if (count <= 1) {
            return null;
        }
        double radius = ownerRef.settingsService.wokDisplayLayoutRadius();
        double angle = (Math.PI * 2D * index) / count;
        return new CookingSettingsService.Vector3(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
    }

    void clearState(StationCoordinates coordinates) {
        ownerRef.clearRuntimeVisuals(coordinates);
        ownerRef.stateStore.deleteAsync(coordinates);
    }

    void clearRuntimeVisuals(StationCoordinates coordinates) {
        Block block = coordinates == null ? null : coordinates.block();
        ownerRef.setWokHeatSourceLit(block, false);
        ownerRef.displayService.removeStation(StationType.WOK, coordinates);
        ownerRef.textDisplayService.removeStation(StationType.WOK, coordinates);
    }

    WokRuntimeService.WokState readState(YamlSection section) {
        if (section == null || !StationType.WOK.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        List<WokRuntimeService.WokIngredientState> ingredients = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("wok.ingredients")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            String source = String.valueOf(normalized.getOrDefault("source", ""));
            if (Texts.isBlank(source)) {
                continue;
            }
            ingredients.add(new WokRuntimeService.WokIngredientState(
                    source,
                    CookingRuntimeUtil.parseInteger(normalized.get("amount"), 1),
                    CookingRuntimeUtil.parseInteger(normalized.get("stir_times"), 0),
                    ownerRef.readItemDataList(normalized.get("items"))
            ));
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        return new WokRuntimeService.WokState(
                ingredients,
                section.getInt("wok.total_stir_count", 0),
                CookingRuntimeUtil.parseLong(section.get("timestamps.last_stir_time_ms"), 0L),
                CookingRuntimeUtil.parseLong(section.get("timestamps.stir_fried_time_ms"), 0L)
        );
    }

    boolean sourceMatches(String left, String right) {
        ItemSourceRef leftSource = ItemSourceUtil.parse(left);
        ItemSourceRef rightSource = ItemSourceUtil.parse(right);
        return ItemSourceUtil.matches(leftSource, rightSource);
    }

    String resolveIngredientSource(Map<String, Object> ingredient) {
        if (ingredient == null) {
            return "";
        }
        Object itemSources = ingredient.get("item_sources");
        if (itemSources == null) {
            return "";
        }
        ItemSourceRef source = ItemSourceUtil.parse(itemSources);
        String shorthand = source == null ? null : ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }

    List<CookingRecipeService.WokIngredientInput> candidateIngredients(WokRuntimeService.WokState state, String source, ItemStack hand) {
        if (Texts.isBlank(source)) {
            return List.of();
        }
        List<CookingRecipeService.WokIngredientInput> candidates = new ArrayList<>();
        if (state != null && state.hasIngredients()) {
            for (WokRuntimeService.WokIngredientState ingredient : state.ingredients()) {
                candidates.add(new CookingRecipeService.WokIngredientInput(ingredient.source(), ingredient.amount()));
            }
        }
        int lastIndex = candidates.size() - 1;
        if (lastIndex >= 0 && ownerRef.sourceMatches(candidates.get(lastIndex).source(), source)) {
            CookingRecipeService.WokIngredientInput last = candidates.get(lastIndex);
            candidates.set(lastIndex, new CookingRecipeService.WokIngredientInput(last.source(), last.amount() + 1, hand));
        } else {
            candidates.add(new CookingRecipeService.WokIngredientInput(source, 1, hand));
        }
        return List.copyOf(candidates);
    }

    List<Map<String, Object>> appendItemData(List<Map<String, Object>> current, Map<String, Object> itemData) {
        List<Map<String, Object>> updated = new ArrayList<>();
        if (current != null) {
            updated.addAll(current);
        }
        if (itemData != null && !itemData.isEmpty()) {
            updated.add(Map.copyOf(itemData));
        }
        return updated.isEmpty() ? List.of() : List.copyOf(updated);
    }

    List<Map<String, Object>> removeLastItemData(List<Map<String, Object>> current) {
        if (current == null || current.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> updated = new ArrayList<>(current);
        updated.remove(updated.size() - 1);
        return updated.isEmpty() ? List.of() : List.copyOf(updated);
    }

    List<Map<String, Object>> readItemDataList(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> itemMap) {
                Map<String, Object> normalized = MapYamlSection.normalizeMap(itemMap);
                if (!normalized.isEmpty()) {
                    items.add(Map.copyOf(normalized));
                }
            }
        }
        return items.isEmpty() ? List.of() : List.copyOf(items);
    }

}
