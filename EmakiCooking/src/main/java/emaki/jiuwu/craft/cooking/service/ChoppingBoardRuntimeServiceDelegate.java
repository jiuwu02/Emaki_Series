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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;

final class ChoppingBoardRuntimeServiceDelegate {

    private final ChoppingBoardRuntimeService ownerRef;

    ChoppingBoardRuntimeServiceDelegate(ChoppingBoardRuntimeService ownerRef) {
        this.ownerRef = ownerRef;
    }

    void setCompletionCoordinator(CookingCompletionCoordinator completionCoordinator) {
        ownerRef.completionCoordinator = completionCoordinator;
        if (completionCoordinator != null) {
            completionCoordinator.register(ownerRef.completionStateAccess());
        }
    }

    boolean handleReturnInput(StationInteraction interaction, Player player, StationCoordinates coordinates, ChoppingBoardRuntimeService.ChoppingBoardState state) {
        if (state == null || !state.hasInputSource()) {
            ownerRef.debugStation("station.chopping_rejected", Map.of(
                    "player", player.getName(),
                    "station", coordinates.runtimeKey(),
                    "operation", "return_input",
                    "reason", state == null ? "no_state" : "no_input_source"
            ));
            return false;
        }
        ownerRef.returnStoredInput(player, coordinates, state);
        interaction.cancel();
        return true;
    }

    String plainItemName(String source) {
        return MiniMessages.plainText(EmakiCoreLibApi.itemDisplayName(source).orElse(""));
    }

    boolean matchesInputSource(String candidateSource, String storedSource) {
        ItemSourceRef candidate = ItemSourceUtil.parse(candidateSource);
        ItemSourceRef stored = ItemSourceUtil.parse(storedSource);
        if (candidate != null && stored != null) {
            return ItemSourceUtil.matches(stored, candidate);
        }
        return candidateSource != null && storedSource != null && candidateSource.equalsIgnoreCase(storedSource);
    }

    ItemStack takeMainHandStack(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return null;
        }
        ItemStack consumed = hand.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        return consumed;
    }

    Map<String, Object> serializeItemTemplate(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        ItemStack template = itemStack.clone();
        template.setAmount(1);
        return StoredItemCodec.serialize(template);
    }

    int addAmounts(int current, int added) {
        long total = (long) Math.max(0, current) + Math.max(0, added);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) total);
    }

    void applyToolDamage(Player player, ItemStack itemStack, int amount) {
        if (player == null || itemStack == null || itemStack.getType().isAir() || amount <= 0) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int maxDurability = itemStack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return;
        }
        int nextDamage = damageable.getDamage() + amount;
        if (nextDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        damageable.setDamage(nextDamage);
        itemStack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(itemStack);
    }

    void maybeDamagePlayer(Player player, Integer chance, Integer value) {
        if (player == null || chance == null || value == null || chance <= 0 || value <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= chance) {
            return;
        }
        player.damage(value);
        CookingRuntimeUtil.sendActionBar(ownerRef.plugin, player, ownerRef.messageService, "chopping_board.cut_hurt", Map.of("damage", value));
    }

    void saveState(StationCoordinates coordinates, ChoppingBoardRuntimeService.ChoppingBoardState state) {
        ownerRef.stateStore.saveAsync(coordinates, serializeState(coordinates, state));
    }

    Map<String, Object> serializeState(StationCoordinates coordinates, ChoppingBoardRuntimeService.ChoppingBoardState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.CHOPPING_BOARD, coordinates);
        if (state.hasInputSource()) {
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("source", state.inputSource());
            inputItem.put("amount", state.inputAmount());
            if (state.inputItemData() != null && !state.inputItemData().isEmpty()) {
                inputItem.put("item", state.inputItemData());
            }
            root.put("input_item", inputItem);
        }
        root.put("timestamps", Map.of("last_interaction_ms", state.lastInteractionMs()));
        root.put("chopping_board", Map.of("cut_count", state.cutCount()));
        return root;
    }

    ChoppingBoardRuntimeService.ChoppingBoardState readState(YamlSection section) {
        if (section == null || !StationType.CHOPPING_BOARD.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return null;
        }
        String inputSource = section.getString("input_item.source", "");
        Map<String, Object> inputItemData = ownerRef.readItemData(section.get("input_item.item"));
        int fallbackAmount = Math.max(1, CookingRuntimeUtil.parseInteger(inputItemData.get("amount"), 1));
        Integer inputAmount = section.getInt("input_item.amount", fallbackAmount);
        Integer cutCount = section.getInt("chopping_board.cut_count", 0);
        UUID displayId = CookingRuntimeUtil.parseUuid(section.getString("display_entity.uuid", ""));
        long lastInteraction = CookingRuntimeUtil.parseLong(section.get("timestamps.last_interaction_ms"), 0L);
        return new ChoppingBoardRuntimeService.ChoppingBoardState(
                inputSource,
                inputItemData,
                inputAmount == null ? fallbackAmount : inputAmount,
                cutCount == null ? 0 : cutCount,
                lastInteraction,
                displayId
        );
    }

    Map<String, Object> readItemData(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(map));
    }

    ItemStack storedItemOrFallback(String sourceText, Map<String, Object> itemData, int amount) {
        ItemStack storedItem = StoredItemCodec.deserialize(itemData);
        if (storedItem != null && !storedItem.getType().isAir()) {
            storedItem.setAmount(Math.max(1, amount));
            return storedItem;
        }
        ItemSourceRef source = ItemSourceUtil.parse(sourceText);
        return source == null ? null : ownerRef.itemSourceService.createItem(source, amount);
    }

    List<ItemStack> storedInputStacks(String sourceText, Map<String, Object> itemData, int amount) {
        ItemStack template = ownerRef.storedItemOrFallback(sourceText, itemData, 1);
        if (template == null || template.getType().isAir()) {
            return List.of();
        }
        int remaining = Math.max(0, amount);
        if (remaining <= 0) {
            return List.of();
        }
        int maxStackSize = Math.max(1, template.getType().getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack stack = template.clone();
            stack.setAmount(stackAmount);
            stacks.add(stack);
            remaining -= stackAmount;
        }
        return List.copyOf(stacks);
    }

    void giveStoredInput(Player player, List<ItemStack> itemStacks) {
        if (player == null || itemStacks == null || itemStacks.isEmpty()) {
            return;
        }
        boolean filledHand = false;
        ItemStack hand = player.getInventory().getItemInMainHand();
        for (ItemStack itemStack : itemStacks) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            if (!filledHand && (hand == null || hand.getType().isAir())) {
                player.getInventory().setItemInMainHand(itemStack);
                filledHand = true;
                continue;
            }
            InventoryItemUtil.giveOrDrop(player, itemStack);
        }
    }

    String inputDisplayName(ChoppingBoardRuntimeService.ChoppingBoardState state) {
        if (state == null || !state.hasInputSource()) {
            return "";
        }
        String storedName = ownerRef.storedItemDisplayName(state.inputItemData());
        if (Texts.isNotBlank(storedName)) {
            return storedName;
        }
        String sourceName = EmakiCoreLibApi.itemDisplayName(state.inputSource()).orElse("");
        return Texts.isBlank(sourceName) ? state.inputSource() : sourceName;
    }

    String storedItemDisplayName(Map<String, Object> itemData) {
        ItemStack storedItem = StoredItemCodec.deserialize(itemData);
        if (storedItem == null || storedItem.getType().isAir()) {
            return "";
        }
        ItemMeta itemMeta = storedItem.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        String displayName = MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
        return Texts.isBlank(displayName) ? "" : displayName;
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

    void clearDisplay(StationCoordinates coordinates, UUID knownId, String inputSource) {
        if (coordinates == null) {
            return;
        }
        ownerRef.displayService.removeStation(StationType.CHOPPING_BOARD, coordinates);
        if (knownId == null) {
            return;
        }
        Location baseLocation = coordinates.location(0.5D, 0.5D, 0.5D);
        Location targetLocation = ownerRef.resolveDisplayLocation(coordinates, inputSource);
        Location searchLocation = targetLocation == null ? baseLocation : targetLocation;
        if (searchLocation != null && searchLocation.getWorld() != null) {
            Entity trackedEntity = Bukkit.getEntity(knownId);
            if (trackedEntity instanceof ItemDisplay) {
                trackedEntity.remove();
            }
        }
        if (searchLocation != null && searchLocation.getWorld() != null) {
            for (Entity entity : searchLocation.getWorld().getNearbyEntities(searchLocation,
                    ChoppingBoardRuntimeService.DISPLAY_SEARCH_RADIUS,
                    ChoppingBoardRuntimeService.DISPLAY_SEARCH_RADIUS,
                    ChoppingBoardRuntimeService.DISPLAY_SEARCH_RADIUS)) {
                if (entity instanceof ItemDisplay) {
                    entity.remove();
                }
            }
        }
    }

    Location resolveDisplayLocation(StationCoordinates coordinates, String inputSource) {
        if (coordinates == null || Texts.isBlank(inputSource)) {
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(inputSource);
        ItemStack itemStack = ownerRef.itemSourceService.createItem(source, 1);
        if (source == null || itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        CookingSettingsService.DisplayAdjustmentProfile adjustment = ownerRef.settingsService.displayAdjustment(
                StationType.CHOPPING_BOARD,
                source,
                itemStack.getType().isBlock()
        );
        return adjustment.applyOffset(coordinates.location(0D, 0D, 0D));
    }

}
