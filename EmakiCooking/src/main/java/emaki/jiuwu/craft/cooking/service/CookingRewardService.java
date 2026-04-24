package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;

public final class CookingRewardService {

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final ActionExecutor actionExecutor;
    private final EmakiItemAssemblyService itemAssemblyService;
    private final CookingLayerSnapshotBuilder snapshotBuilder = new CookingLayerSnapshotBuilder();

    public CookingRewardService(JavaPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService,
            ActionExecutor actionExecutor,
            EmakiItemAssemblyService itemAssemblyService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.itemSourceService = itemSourceService;
        this.actionExecutor = actionExecutor;
        this.itemAssemblyService = itemAssemblyService;
    }

    public void deliver(RecipeDocument recipe,
            Player player,
            Location location,
            boolean dropResult,
            List<Map<String, Object>> outputs,
            List<String> actions,
            String phase,
            Map<String, ?> placeholders) {
        for (Map<String, Object> output : outputs == null ? List.<Map<String, Object>>of() : outputs) {
            deliverOutput(recipe, player, location, dropResult, output, phase, placeholders);
        }
        executeActions(actions, player, location, phase, defaultPlaceholders(player, location, placeholders));
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
        ItemSource source = ItemSourceUtil.parse(output.get("source"));
        if (source == null) {
            return null;
        }
        int amount = resolveAmount(output);
        if (amount <= 0) {
            return null;
        }
        if (itemAssemblyService == null) {
            return itemSourceService.createItem(source, amount);
        }
        Map<String, Object> effectivePlaceholders = buildOutputPlaceholders(recipe, output, player, location, phase, placeholders);
        ItemStack itemStack = itemAssemblyService.preview(new EmakiItemAssemblyRequest(
                source,
                amount,
                null,
                List.of(snapshotBuilder.buildSnapshot(recipe, output, phase, effectivePlaceholders))
        ));
        return itemStack == null ? itemSourceService.createItem(source, amount) : itemStack;
    }

    private void deliverOutput(RecipeDocument recipe,
            Player player,
            Location location,
            boolean dropResult,
            Map<String, Object> output,
            String phase,
            Map<String, ?> placeholders) {
        if (output == null || output.isEmpty() || !passesChance(output.get("chance"))) {
            return;
        }
        ItemStack itemStack = createOutputItem(recipe, output, player, location, phase, placeholders);
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        if (!deliverItem(player, location, dropResult, itemStack)) {
            return;
        }
        executeActions(
                outputActions(output),
                player,
                location,
                phase,
                buildOutputPlaceholders(recipe, output, player, location, phase, placeholders)
        );
    }

    private boolean deliverItem(Player player, Location location, boolean dropResult, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        if (dropResult || player == null) {
            Location dropLocation = location == null ? (player == null ? null : player.getLocation()) : location;
            return dropAt(dropLocation, itemStack);
        }
        InventoryItemUtil.giveOrDrop(player, itemStack);
        return true;
    }

    private boolean dropAt(Location location, ItemStack itemStack) {
        if (location == null || location.getWorld() == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        location.getWorld().dropItemNaturally(location, itemStack);
        return true;
    }

    private void executeActions(List<String> actions,
            Player player,
            Location location,
            String phase,
            Map<String, ?> placeholders) {
        if (actions == null || actions.isEmpty() || actionExecutor == null) {
            return;
        }
        ActionContext context = ActionContext.create(plugin, player, phase, false)
                .withPlaceholders(defaultPlaceholders(player, location, placeholders));
        actionExecutor.executeAll(context, actions, true).whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Failed to execute cooking actions: " + throwable.getMessage());
                return;
            }
            if (result instanceof ActionBatchResult batchResult && !batchResult.success()) {
                plugin.getLogger().warning("Cooking action batch finished with failures in phase " + phase + ".");
            }
        });
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
        Map<String, Object> values = new LinkedHashMap<>(defaultPlaceholders(player, location, placeholders));
        if (recipe != null) {
            values.put("recipe_id", recipe.id());
            values.put("recipe_name", recipe.displayName());
            values.put("station_type", recipe.stationType().folderName());
        }
        if (Texts.isNotBlank(phase)) {
            values.put("phase", phase);
        }
        if (output != null && !output.isEmpty()) {
            putIfPresent(values, "output_source", output.get("source"));
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

    private Map<String, ?> defaultPlaceholders(Player player, Location location, Map<String, ?> placeholders) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (placeholders != null) {
            values.putAll(placeholders);
        }
        if (player != null) {
            values.put("player", player.getName());
        }
        if (location != null && location.getWorld() != null) {
            values.put("world", location.getWorld().getName());
            values.put("x", location.getBlockX());
            values.put("y", location.getBlockY());
            values.put("z", location.getBlockZ());
        }
        return Map.copyOf(values);
    }

    private List<String> outputActions(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        Object rawActions = output.get("actions");
        if (!(rawActions instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> actions = new java.util.ArrayList<>();
        for (Object raw : list) {
            if (raw != null) {
                actions.add(String.valueOf(raw));
            }
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
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

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target == null || Texts.isBlank(key) || value == null) {
            return;
        }
        target.put(key, value);
    }
}
