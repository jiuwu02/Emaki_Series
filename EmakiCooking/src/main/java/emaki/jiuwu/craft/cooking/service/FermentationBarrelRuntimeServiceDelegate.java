package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import emaki.jiuwu.craft.cooking.CookingPermissions;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;

final class FermentationBarrelRuntimeServiceDelegate {

    private final FermentationBarrelRuntimeService ownerRef;

    FermentationBarrelRuntimeServiceDelegate(FermentationBarrelRuntimeService ownerRef) {
        this.ownerRef = ownerRef;
    }

    boolean matchesInputMatchers(RecipeDocument recipe, FermentationBarrelState state, Player player) {
        for (Map<String, Object> input : ownerRef.recipeService.fermentationInputs(recipe)) {
            ItemRequirement requirement = CookingMatchers.requirement(input, "item_sources", "matcher");
            if (requirement.empty()) {
                return false;
            }
            String expectedSlotId = ownerRef.inputSlotId(input);
            if (!ownerRef.matchesSlotWithId(state, expectedSlotId, requirement, player)) {
                return false;
            }
        }
        return true;
    }

    boolean matchesSlotWithId(FermentationBarrelState state, String expectedSlotId, ItemRequirement requirement, Player player) {
        boolean matched = false;
        for (Map.Entry<Integer, String> entry : state.slotIds().entrySet()) {
            if (Texts.isNotBlank(expectedSlotId) && !expectedSlotId.equalsIgnoreCase(entry.getValue())) {
                continue;
            }
            ItemStack stored = StoredItemCodec.deserialize(state.slotItemData(entry.getKey()));
            if (stored == null || stored.getType().isAir()) {
                continue;
            }
            if (!requirement.test(stored, ItemSourceUtil.parse(state.slotSources().get(entry.getKey())), player)) {
                return false;
            }
            matched = true;
        }
        return matched;
    }

    Map<String, Integer> aggregateActual(FermentationBarrelState state) {
        return FermentationIdentityResolver.aggregate(state.slotCountKeys(), state.slotAmounts());
    }

    Map<String, Integer> aggregateExpected(RecipeDocument recipe) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> input : ownerRef.recipeService.fermentationInputs(recipe)) {
            String countKey = ownerRef.inputCountKey(input);
            int amount = Math.max(1, CookingRuntimeUtil.parseInteger(input.get("amount"), 1));
            if (Texts.isNotBlank(countKey)) {
                result.merge(countKey, amount, Integer::sum);
            }
        }
        return result;
    }

    String inputSlotId(Map<String, Object> input) {
        String declared = Texts.toStringSafe(input == null ? null : input.get("slot_id")).trim();
        return Texts.isNotBlank(declared) ? declared.toLowerCase(Locale.ROOT) : "";
    }

    String inputCountKey(Map<String, Object> input) {
        String declared = Texts.toStringSafe(input == null ? null : input.get("count_key")).trim();
        return Texts.isNotBlank(declared) ? declared.toLowerCase(Locale.ROOT) : "";
    }

    CookingInputIngredient fermentationInputBySlotId(RecipeDocument recipe, String slotId) {
        if (Texts.isBlank(slotId)) {
            return null;
        }
        for (CookingInputIngredient input : ownerRef.recipeService.fermentationInputIngredients(recipe)) {
            if (slotId.equalsIgnoreCase(input.slotId())) {
                return input;
            }
        }
        return null;
    }

    boolean invalidFermentationIdentities(RecipeDocument recipe) {
        List<FermentationIdentityResolver.Identity> identities = new ArrayList<>();
        for (Map<String, Object> input : ownerRef.recipeService.fermentationInputs(recipe)) {
            String slotId = ownerRef.inputSlotId(input);
            String countKey = ownerRef.inputCountKey(input);
            if (Texts.isBlank(slotId) || Texts.isBlank(countKey)) {
                return true;
            }
            identities.add(new FermentationIdentityResolver.Identity(slotId, countKey));
        }
        return identities.isEmpty() || FermentationIdentityResolver.hasDuplicateSlotIds(identities);
    }

    FermentationStage currentFermentationStage(FermentationBarrelState state, long now) {
        if (state == null) {
            return FermentationStage.COMPLETE;
        }
        RecipeDocument recipe = ownerRef.recipeService.fermentationBarrelRecipeById(state.activeRecipeId());
        if (recipe == null) {
            return FermentationStage.COMPLETE;
        }
        if (state.completed()) {
            int overSeconds = ownerRef.recipeService.fermentationOverTimeSeconds(recipe);
            if (overSeconds > 0 && now >= state.finishAtMs() + overSeconds * 1000L) {
                return FermentationStage.OVER;
            }
            return FermentationStage.COMPLETE;
        }
        if (state.fermenting()) {
            double earlyRatio = ownerRef.recipeService.fermentationEarlyMinProgressRatio(recipe);
            if (earlyRatio >= 0.0D) {
                long total = Math.max(1L, state.finishAtMs() - state.startedAtMs());
                long done = Math.max(0L, now - state.startedAtMs());
                if ((double) done / (double) total >= earlyRatio) {
                    return FermentationStage.EARLY;
                }
            }
        }
        return FermentationStage.COMPLETE;
    }

    String collectionMessage(FermentationStage stage) {
        if (stage == FermentationStage.EARLY) {
            return "fermentation_barrel.collected_early";
        }
        if (stage == FermentationStage.OVER) {
            return "fermentation_barrel.collected_over";
        }
        return "fermentation_barrel.collected";
    }

    String calculateProgress(FermentationBarrelState state, long now) {
        if (!state.fermenting() && !state.completed()) {
            return "0.00%";
        }
        if (state.completed()) {
            return "100.00%";
        }
        long total = Math.max(1L, state.finishAtMs() - state.startedAtMs());
        long done = Math.max(0L, now - state.startedAtMs());
        return String.format(Locale.ROOT, "%.2f%%", Math.min(100D, (double) done * 100D / (double) total));
    }

    void waitForInFlightTicks() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!ownerRef.tickingStations.isEmpty() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void dropOriginalItems(Block block, FermentationBarrelState state) {
        if (block.getWorld() == null) {
            return;
        }
        Location location = block.getLocation().add(0.5D, 1.0D, 0.5D);
        for (Map.Entry<Integer, String> entry : ownerRef.codec.sortedSlots(state.slotSources()).entrySet()) {
            ItemStack item = ownerRef.codec.deserializeItem(state.slotItemData(entry.getKey()));
            if (item == null || item.getType().isAir()) {
                ItemSourceRef source = ItemSourceUtil.parse(entry.getValue());
                item = source == null ? null : ownerRef.itemSourceService.createItem(source, state.slotAmounts().getOrDefault(entry.getKey(), 1));
            }
            if (item != null && !item.getType().isAir()) {
                block.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    FermentationBarrelState saveInventory(StationCoordinates coordinates, Inventory inventory, UUID playerUuid, String playerName) {
        FermentationBarrelState state = ownerRef.guiController.snapshotInventoryState(coordinates, inventory, playerUuid, playerName);
        ownerRef.saveState(coordinates, state);
        return state;
    }

    Optional<StationCoordinates> viewingStation(UUID viewerId) {
        return Optional.ofNullable(ownerRef.guiController.viewingCoordinates(viewerId));
    }

    void removeState(StationCoordinates coordinates, boolean deleteFile) {
        ownerRef.runtimeStates.remove(coordinates);
        if (coordinates != null) {
            ownerRef.textDisplayService.removeStation(StationType.FERMENTATION_BARREL, coordinates);
        }
        if (deleteFile) {
            ownerRef.stateStore.deleteAsync(coordinates);
        }
    }

    void appendLine(StringBuilder builder, String line) {
        if (Texts.isBlank(line)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    void flushAll() {
        for (Map.Entry<StationCoordinates, FermentationBarrelState> entry : ownerRef.runtimeStates.entrySet()) {
            FermentationBarrelState state = entry.getValue();
            if (state.valid() && state.slotIdsResolved() && !state.isCompletelyEmpty()) {
                ownerRef.stateStore.saveAsync(entry.getKey(), ownerRef.codec.serializeState(entry.getKey(), state));
            }
        }
    }

}
