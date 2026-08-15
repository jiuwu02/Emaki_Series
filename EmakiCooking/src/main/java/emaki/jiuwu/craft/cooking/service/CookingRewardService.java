package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.api.event.CookingRecipeCompleteEvent;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;

public final class CookingRewardService {

    private final JavaPlugin plugin;
    @SuppressWarnings("unused")
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final ActionLineRunner actionLines;
    private final EmakiItemAssemblyService itemAssemblyService;
    private final EmakiScheduling taskScheduler;
    private final ThreadOwnership threadOwnership;
    private final CookingLayerSnapshotBuilder snapshotBuilder = new CookingLayerSnapshotBuilder();
    private CookingRecipeService recipeService;

    public CookingRewardService(JavaPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService,
            ActionLineRunner actionLines,
            EmakiItemAssemblyService itemAssemblyService,
            EmakiScheduling taskScheduler,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.itemSourceService = itemSourceService;
        this.actionLines = actionLines;
        this.itemAssemblyService = itemAssemblyService;
        this.taskScheduler = taskScheduler;
        this.threadOwnership = threadOwnership;
    }

    public void setRecipeService(CookingRecipeService recipeService) {
        this.recipeService = recipeService;
    }

    public void deliver(RecipeDocument recipe,
            Player player,
            Location location,
            boolean dropResult,
            List<CookingInputIngredient> inputs,
            List<Map<String, Object>> outputs,
            List<String> actions,
            String phase,
            Map<String, ?> placeholders) {
        PreparedReward prepared = prepare(
                UUID.randomUUID().toString(),
                recipe,
                player,
                location,
                dropResult,
                inputs,
                outputs,
                actions,
                phase,
                placeholders
        );
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (FrozenRewardUnit unit : prepared.units()) {
            chain = chain.thenCompose(ignored -> executeFrozen(unit.kind(), unit.payload()));
        }
        chain.exceptionally(error -> {
            plugin.getLogger().warning("Failed to execute legacy cooking reward: " + error.getMessage());
            return false;
        });
    }

    PreparedReward prepare(String operationId,
            RecipeDocument recipe,
            Player player,
            Location location,
            boolean dropResult,
            List<CookingInputIngredient> inputs,
            List<Map<String, Object>> outputs,
            List<String> actions,
            String phase,
            Map<String, ?> placeholders) {
        return prepare(operationId, recipe, player, location, dropResult, inputs, outputs, actions, phase,
                placeholders, null);
    }

    PreparedReward prepare(String operationId,
            RecipeDocument recipe,
            Player player,
            Location location,
            boolean dropResult,
            List<CookingInputIngredient> inputs,
            List<Map<String, Object>> outputs,
            List<String> actions,
            String phase,
            Map<String, ?> placeholders,
            CookingCompletionRequest.ConditionOutcome conditionOutcome) {
        String stableOperationId = Texts.isBlank(operationId) ? UUID.randomUUID().toString() : operationId;
        Map<String, Object> basePlaceholders = defaultPlaceholders(recipe, player, location, inputs, placeholders);
        List<FrozenRewardUnit> units = new ArrayList<>();
        int sequence = 0;

        if (recipe != null && recipeService != null && recipeService.hasCompletionCondition(recipe)) {
            boolean conditionPassed = conditionOutcome != null
                    ? conditionOutcome.passed()
                    : recipeService.completionConditionPasses(recipe, player);
            List<String> branchActions = recipeService.completionConditionActions(recipe, conditionPassed);
            if (!branchActions.isEmpty()) {
                units.add(freezeActionUnit(
                        stableOperationId,
                        sequence++,
                        branchActions,
                        player,
                        location,
                        phase,
                        basePlaceholders
                ));
            }
            if (!conditionPassed && recipeService.completionConditionBlocksOutput(recipe)) {
                return new PreparedReward(units);
            }
        }

        String effectivePhase = Texts.toStringSafe(phase);
        List<Map<String, Object>> effectiveOutputs = outputs == null ? List.of() : outputs;
        List<String> effectiveActions = actions == null ? List.of() : actions;

        boolean effectiveDropResult = dropResult;
        if (recipe != null && threadOwnership != null && threadOwnership.isGlobalOwned()) {
            CookingRecipeCompleteEvent completeEvent = new CookingRecipeCompleteEvent(
                    player,
                    location,
                    recipe.id(),
                    recipe.displayName(),
                    recipe.stationType() == null ? "" : recipe.stationType().folderName(),
                    effectivePhase,
                    effectiveOutputs.size(),
                    dropResult
            );
            Bukkit.getPluginManager().callEvent(completeEvent);
            if (completeEvent.isCancelled()) {
                return new PreparedReward(units);
            }
            effectiveDropResult = completeEvent.isDropResult();
        }

        for (Map<String, Object> output : effectiveOutputs) {
            if (output == null || output.isEmpty() || !passesChance(output.get("chance"))) {
                continue;
            }
            ItemStack itemStack = createOutputItem(recipe, output, player, location, effectivePhase, basePlaceholders);
            if (itemStack == null || itemStack.getType().isAir()) {
                plugin.getLogger().warning("[CookingReward] Output item is null or air. output_map=" + output
                        + ", recipe=" + (recipe == null ? "null" : recipe.id()));
                continue;
            }
            units.add(freezeItemUnit(
                    stableOperationId,
                    sequence++,
                    itemStack,
                    player,
                    location,
                    effectiveDropResult
            ));
            List<String> outputActions = Texts.asStringList(output.get("actions"));
            if (!outputActions.isEmpty()) {
                units.add(freezeActionUnit(
                        stableOperationId,
                        sequence++,
                        outputActions,
                        player,
                        location,
                        effectivePhase,
                        buildOutputPlaceholders(recipe, output, player, location, effectivePhase, basePlaceholders)
                ));
            }
        }
        if (!effectiveActions.isEmpty()) {
            units.add(freezeActionUnit(
                    stableOperationId,
                    sequence,
                    effectiveActions,
                    player,
                    location,
                    effectivePhase,
                    basePlaceholders
            ));
        }
        return new PreparedReward(units);
    }

    CompletableFuture<Boolean> executeFrozen(RewardUnitKind kind, Map<String, Object> payload) {
        if (kind == null) {
            return CompletableFuture.completedFuture(false);
        }
        return switch (kind) {
            case ITEM_REWARD -> executeFrozenItem(payload);
            case ACTION_BATCH -> executeFrozenActions(payload);
        };
    }

    private CompletableFuture<Boolean> executeFrozenItem(Map<String, Object> payload) {
        Map<String, Object> itemData = mapValue(payload == null ? null : payload.get("item"));
        ItemStack itemStack = StoredItemCodec.deserialize(itemData);
        if (itemStack == null || itemStack.getType().isAir()) {
            return CompletableFuture.completedFuture(false);
        }
        Player player = resolvePlayer(payload == null ? null : payload.get("player_uuid"));
        Location location = resolveLocation(mapValue(payload == null ? null : payload.get("location")));
        boolean dropResult = boolValue(payload == null ? null : payload.get("drop_result"), false);

        if (!dropResult && player != null && player.isOnline()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            try {
                TaskToken handle = taskScheduler.runForEntity(plugin, player, () -> {
                    try {
                        InventoryItemUtil.giveOrDrop(player, itemStack.clone());
                        future.complete(true);
                    } catch (Throwable error) {
                        future.completeExceptionally(error);
                    }
                }, () -> future.complete(false));
                if (handle == null) {
                    future.complete(false);
                }
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
            return future;
        }
        Location dropLocation = location != null ? location : (player == null ? null : player.getLocation());
        if (dropLocation == null || dropLocation.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
                TaskToken handle = taskScheduler.runAtLocation(plugin, dropLocation, () -> {
                    try {
                        World world = dropLocation.getWorld();
                        if (world == null) {
                            future.complete(false);
                            return;
                        }
                        world.dropItemNaturally(dropLocation, itemStack.clone());
                        future.complete(true);
                    } catch (Throwable error) {
                        future.completeExceptionally(error);
                    }
                });
                if (handle == null) {
                    future.complete(false);
                }
        } catch (Throwable error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private CompletableFuture<Boolean> executeFrozenActions(Map<String, Object> payload) {
        List<String> actions = Texts.asStringList(payload == null ? null : payload.get("actions"));
        if (actions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        if (actionLines == null) {
            return CompletableFuture.completedFuture(false);
        }
        Player player = resolvePlayer(payload == null ? null : payload.get("player_uuid"));
        Location location = resolveLocation(mapValue(payload == null ? null : payload.get("location")));
        String phase = Texts.toStringSafe(payload == null ? null : payload.get("phase"));
        Map<String, Object> placeholders = mapValue(payload == null ? null : payload.get("placeholders"));

        PipelineContext context = actionLines.context(player, phase, false, placeholders)
                .withOrigin(location);
        return actionLines.run(actions, context, true);
    }

    public boolean completionConditionPasses(RecipeDocument recipe, Player player) {
        return recipeService == null || recipeService.completionConditionPasses(recipe, player);
    }

    public boolean completionConditionBlocksOutput(RecipeDocument recipe) {
        return recipeService != null && recipeService.completionConditionBlocksOutput(recipe);
    }

    public ConditionGate evaluateConditionGate(RecipeDocument recipe, Player player) {
        if (recipe == null || recipeService == null || !recipeService.hasCompletionCondition(recipe)) {
            return ConditionGate.OPEN;
        }
        boolean passed = recipeService.completionConditionPasses(recipe, player);
        boolean blocked = !passed && recipeService.completionConditionBlocksOutput(recipe);
        return new ConditionGate(
                CookingCompletionRequest.ConditionOutcome.of(passed),
                blocked,
                blocked ? recipeService.completionConditionActions(recipe, false) : List.of()
        );
    }

    public record ConditionGate(
            CookingCompletionRequest.ConditionOutcome outcome,
            boolean blocked,
            List<String> failActions) {

        static final ConditionGate OPEN = new ConditionGate(null, false, List.of());

        public ConditionGate {
            failActions = failActions == null ? List.of() : List.copyOf(failActions);
        }
    }

    public void runConditionFailActions(List<String> actions,
            Player player,
            Location location,
            String phase,
            Map<String, ?> placeholders) {
        if (actions == null || actions.isEmpty() || actionLines == null) {
            return;
        }
        PipelineContext context = actionLines.context(player, Texts.toStringSafe(phase), false, immutableMap(placeholders))
                .withOrigin(location);
        actionLines.run(actions, context, true);
    }

    public ItemStack createOutputItem(RecipeDocument recipe,
            Map<String, Object> output,
            Player player,
            Location location,
            String phase,
            Map<String, ?> placeholders) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(output.get("item_sources"));
        if (source == null) {
            plugin.getLogger().warning("[CookingReward] Failed to parse item_sources from output: " + output.get("item_sources"));
            return null;
        }
        int amount = resolveAmount(output);
        if (amount <= 0) {
            return null;
        }
        if (itemAssemblyService == null) {
            ItemStack directItem = itemSourceService.createItem(source, amount);
            if (directItem == null) {
                plugin.getLogger().warning("[CookingReward] itemSourceService.createItem returned null for source="
                        + ItemSourceUtil.toShorthand(source) + " amount=" + amount);
            }
            return directItem;
        }
        Map<String, Object> effectivePlaceholders = buildOutputPlaceholders(recipe, output, player, location, phase, placeholders);
        ItemStack itemStack = itemAssemblyService.preview(new EmakiItemAssemblyRequest(
                source,
                amount,
                null,
                List.of(snapshotBuilder.buildSnapshot(recipe, output, phase, effectivePlaceholders))
        ));
        if (itemStack == null) {
            ItemStack fallbackItem = itemSourceService.createItem(source, amount);
            if (fallbackItem == null) {
                plugin.getLogger().warning("[CookingReward] Both assembly preview and direct createItem returned null for source="
                        + ItemSourceUtil.toShorthand(source) + " type=" + source.kind() + " id=" + source.identifier());
            }
            return fallbackItem;
        }
        return itemStack;
    }

    private FrozenRewardUnit freezeItemUnit(String operationId,
            int sequence,
            ItemStack itemStack,
            Player player,
            Location location,
            boolean dropResult) {
        Map<String, Object> payload = targetPayload(player, location);
        payload.put("item", StoredItemCodec.serialize(itemStack));
        payload.put("drop_result", dropResult);
        return new FrozenRewardUnit(stableUnitId(operationId, sequence), RewardUnitKind.ITEM_REWARD, payload);
    }

    private FrozenRewardUnit freezeActionUnit(String operationId,
            int sequence,
            List<String> actions,
            Player player,
            Location location,
            String phase,
            Map<String, ?> placeholders) {
        Map<String, Object> payload = targetPayload(player, location);
        payload.put("actions", actions == null ? List.of() : List.copyOf(actions));
        payload.put("phase", Texts.toStringSafe(phase));
        payload.put("placeholders", immutableMap(placeholders));
        return new FrozenRewardUnit(stableUnitId(operationId, sequence), RewardUnitKind.ACTION_BATCH, payload);
    }

    private String stableUnitId(String operationId, int sequence) {
        return operationId + ":reward:" + String.format(Locale.ROOT, "%04d", Math.max(0, sequence));
    }

    private Map<String, Object> targetPayload(Player player, Location location) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player_uuid", player == null ? "" : player.getUniqueId().toString());
        payload.put("player_name", player == null ? "" : player.getName());
        payload.put("location", locationPayload(location));
        return payload;
    }

    private Map<String, Object> locationPayload(Location location) {
        if (location == null || location.getWorld() == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("world", location.getWorld().getName());
        result.put("x", location.getX());
        result.put("y", location.getY());
        result.put("z", location.getZ());
        result.put("yaw", location.getYaw());
        result.put("pitch", location.getPitch());
        return Map.copyOf(result);
    }

    private Location resolveLocation(Map<String, Object> serialized) {
        if (serialized.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(Texts.toStringSafe(serialized.get("world")));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                doubleValue(serialized.get("x"), 0.0D),
                doubleValue(serialized.get("y"), 0.0D),
                doubleValue(serialized.get("z"), 0.0D),
                (float) doubleValue(serialized.get("yaw"), 0.0D),
                (float) doubleValue(serialized.get("pitch"), 0.0D)
        );
    }

    private Player resolvePlayer(Object rawUuid) {
        String text = Texts.toStringSafe(rawUuid);
        if (Texts.isBlank(text)) {
            return null;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(text));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean passesChance(Object rawChance) {
        Integer chance = parseInteger(rawChance, 100);
        if (chance == null || chance >= 100) {
            return true;
        }
        if (chance <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < chance;
    }

    private int resolveAmount(Map<String, Object> output) {
        Map<String, Object> amountRange = output.get("amount_range") instanceof Map<?, ?> amountRangeMap
                ? MapYamlSection.normalizeMap(amountRangeMap)
                : Map.of();
        if (!amountRange.isEmpty()) {
            Integer min = parseInteger(amountRange.get("min"), 1);
            Integer max = parseInteger(amountRange.get("max"), min);
            if (min == null || max == null) {
                return 1;
            }
            if (min > max) {
                int swap = min;
                min = max;
                max = swap;
            }
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        Integer amount = parseInteger(output.get("amount"), 1);
        return amount == null ? 1 : Math.max(1, amount);
    }

    private Map<String, Object> buildOutputPlaceholders(RecipeDocument recipe,
            Map<String, Object> output,
            Player player,
            Location location,
            String phase,
            Map<String, ?> placeholders) {
        Map<String, Object> values = new LinkedHashMap<>(defaultPlaceholders(recipe, player, location, List.of(), placeholders));
        if (Texts.isNotBlank(phase)) {
            values.put("phase", phase);
        }
        if (output != null && !output.isEmpty()) {
            putIfPresent(values, "output_source", outputSourceShorthand(output));
            putIfPresent(values, "output_amount", output.get("amount"));
            putIfPresent(values, "output_chance", output.get("chance"));
            if (output.get("amount_range") instanceof Map<?, ?> amountRange) {
                Map<String, Object> normalizedRange = MapYamlSection.normalizeMap(amountRange);
                putIfPresent(values, "output_amount_min", normalizedRange.get("min"));
                putIfPresent(values, "output_amount_max", normalizedRange.get("max"));
            }
        }
        return Map.copyOf(values);
    }

    private Map<String, Object> defaultPlaceholders(RecipeDocument recipe,
            Player player,
            Location location,
            List<CookingInputIngredient> inputs,
            Map<String, ?> placeholders) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (placeholders != null) {
            values.putAll(placeholders);
        }
        if (recipe != null) {
            values.put("recipe_id", recipe.id());
            values.put("recipe_name", recipe.displayName());
            values.put("station_type", recipe.stationType() == null ? "" : recipe.stationType().folderName());
            values.put("cooking_recipe_id", recipe.id());
            values.put("cooking_recipe_name", recipe.displayName());
            values.put("cooking_station_type", recipe.stationType() == null ? "" : recipe.stationType().folderName());
        }
        if (inputs != null) {
            values.put("input_count", inputs.size());
        }
        if (player != null) {
            values.put("player", player.getName());
            values.put("player_uuid", player.getUniqueId().toString());
        }
        if (location != null && location.getWorld() != null) {
            values.put("world", location.getWorld().getName());
            values.put("x", location.getBlockX());
            values.put("y", location.getBlockY());
            values.put("z", location.getBlockZ());
        }
        return Map.copyOf(values);
    }

    private String outputSourceShorthand(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        ItemSourceRef source = ItemSourceUtil.parse(output.get("item_sources"));
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
    }

    private Integer parseInteger(Object raw, Integer fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(Texts.toStringSafe(raw).trim());
        } catch (Exception _) {
            return fallback;
        }
    }

    private double doubleValue(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean boolValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(raw));
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target == null || Texts.isBlank(key) || value == null) {
            return;
        }
        target.put(key, value);
    }

    private Map<String, Object> mapValue(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(map));
    }

    private Map<String, Object> immutableMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Object plain = ConfigNodes.toPlainData(values);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(map));
    }

    enum RewardUnitKind {
        ITEM_REWARD,
        ACTION_BATCH
    }

    record FrozenRewardUnit(String stableId, RewardUnitKind kind, Map<String, Object> payload) {

        FrozenRewardUnit {
            stableId = Texts.toStringSafe(stableId);
            payload = payload == null ? Map.of() : immutablePayload(payload);
        }

        private static Map<String, Object> immutablePayload(Map<String, Object> payload) {
            Object plain = ConfigNodes.toPlainData(payload);
            if (!(plain instanceof Map<?, ?> map)) {
                return Map.of();
            }
            return Map.copyOf(MapYamlSection.normalizeMap(map));
        }
    }

    record PreparedReward(List<FrozenRewardUnit> units) {

        PreparedReward {
            units = units == null ? List.of() : List.copyOf(units);
        }
    }
}
