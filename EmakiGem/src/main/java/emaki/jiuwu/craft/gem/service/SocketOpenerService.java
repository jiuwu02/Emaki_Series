package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.event.GemSocketOpenEvent;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;

public final class SocketOpenerService {

    private static final int NO_AVAILABLE_SLOT = -1;
    private static final int SLOT_ALREADY_OPENED = -2;

    public record Result(boolean success, String messageKey, Map<String, Object> placeholders) {

        public Result {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        }

        public static Result success(String messageKey, Map<String, Object> placeholders) {
            return new Result(true, messageKey, placeholders);
        }

        public static Result failure(String messageKey, Map<String, Object> placeholders) {
            return new Result(false, messageKey, placeholders);
        }
    }

    public record OpenResult(Result result, ItemStack updatedEquipment, ItemStack updatedOpener) {
    }

    private final EmakiGemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final GemItemMatcher itemMatcher;
    private final GemItemFactory itemFactory;
    private final GemStateService stateService;
    private final GemActionCoordinator actionCoordinator;

    public SocketOpenerService(EmakiGemPlugin plugin,
            GemItemMatcher itemMatcher,
            GemItemFactory itemFactory,
            GemStateService stateService,
            GemActionCoordinator actionCoordinator,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.itemMatcher = itemMatcher;
        this.itemFactory = itemFactory;
        this.stateService = stateService;
        this.actionCoordinator = actionCoordinator;
    }

    public Result open(Player actor, Player target, String openerId, boolean bypassRequirement) {
        return open(actor, target, openerId, null, bypassRequirement);
    }

    public Result openAt(Player actor, Player target, String openerId, int slotIndex, boolean bypassRequirement) {
        return open(actor, target, openerId, slotIndex, bypassRequirement);
    }

    public OpenResult openDirect(Player actor,
            ItemStack equipment,
            ItemStack openerItem) {
        SocketOpenerConfig opener = itemMatcher.matchOpenerItem(openerItem);
        if (opener == null) {
            return new OpenResult(Result.failure("command.open.opener_not_found", Map.of()), equipment, openerItem);
        }
        return openDirect(actor, equipment, openerItem, opener.id(), null, false, true);
    }

    public OpenResult openDirect(Player actor,
            ItemStack equipment,
            ItemStack openerItem,
            String openerId,
            int slotIndex,
            boolean bypassRequirement) {
        Integer preferredSlot = slotIndex < 0 ? null : slotIndex;
        return openDirect(actor, equipment, openerItem, openerId, preferredSlot, bypassRequirement, true);
    }

    private OpenResult openDirect(Player actor,
            ItemStack equipment,
            ItemStack openerItem,
            String openerId,
            Integer preferredSlotIndex,
            boolean bypassRequirement,
            boolean direct) {
        if (actor == null) {
            return new OpenResult(Result.failure("general.player_not_found", Map.of()), equipment, openerItem);
        }
        SocketOpenerConfig opener = plugin.appConfig().socketOpeners().get(Texts.lower(openerId));
        if (opener == null || !opener.enabled()) {
            return new OpenResult(Result.failure("command.open.opener_not_found", Map.of("opener", openerId)), equipment, openerItem);
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return new OpenResult(Result.failure("gem.error.invalid_equipment", Map.of("player", actor.getName())), equipment, openerItem);
        }
        if (!evaluateConditions(actor)) {
            return new OpenResult(Result.failure("gem.error.condition_not_met", Map.of()), equipment, openerItem);
        }
        if (!bypassRequirement && !itemMatcher.matchesOpenerItem(openerItem, opener)) {
            return new OpenResult(Result.failure("command.open.hold_opener", Map.of("opener", opener.id())), equipment, openerItem);
        }
        GemState currentState = stateService.resolveState(equipment, itemDefinition);
        int resolvedSlotIndex = resolveTargetSlot(itemDefinition, currentState, opener, preferredSlotIndex);
        if (resolvedSlotIndex == SLOT_ALREADY_OPENED) {
            return new OpenResult(failureWithActions(actor, opener, itemDefinition, preferredSlotIndex == null ? -1 : preferredSlotIndex,
                    "command.open.slot_already_opened", Map.of()), equipment, openerItem);
        }
        if (resolvedSlotIndex < 0) {
            return new OpenResult(failureWithActions(actor, opener, itemDefinition, preferredSlotIndex == null ? -1 : preferredSlotIndex,
                    preferredSlotIndex == null ? "command.open.no_available_slot" : "command.open.slot_unavailable", Map.of()), equipment, openerItem);
        }

        if (scheduling.ownsEntity(actor)) {
            GemSocketOpenEvent openEvent = new GemSocketOpenEvent(UUID.randomUUID().toString(), actor,
                    equipment, openerItem, opener.id(), resolvedSlotIndex, itemDefinition.id());
            Bukkit.getPluginManager().callEvent(openEvent);
            if (openEvent.isCancelled()) {
                return new OpenResult(Result.failure("gem.error.condition_not_met", Map.of()), equipment, openerItem);
            }
        }
        GemState nextState = currentState.withOpenedSlots(List.of(resolvedSlotIndex));
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            return new OpenResult(failureWithActions(actor, opener, itemDefinition, resolvedSlotIndex, "command.open.apply_failed", Map.of()), equipment, openerItem);
        }
        ItemStack updatedOpener = consumeOne(openerItem, bypassRequirement || !opener.consumeOnSuccess());
        Map<String, Object> placeholders = basePlaceholders(actor, opener, itemDefinition, resolvedSlotIndex);
        actionCoordinator.execute(actor, "gem_socket_open", opener.successActions(), placeholders);
        return new OpenResult(Result.success("command.open.success", placeholders), rebuilt, updatedOpener);
    }

    private Result open(Player actor, Player target, String openerId, Integer preferredSlotIndex, boolean bypassRequirement) {
        if (target == null) {
            return Result.failure("general.player_not_found", Map.of());
        }
        SocketOpenerConfig opener = plugin.appConfig().socketOpeners().get(Texts.lower(openerId));
        if (opener == null || !opener.enabled()) {
            return Result.failure("command.open.opener_not_found", Map.of("opener", openerId));
        }
        ItemStack equipment = target.getInventory().getItemInMainHand();
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Result.failure("gem.error.invalid_equipment", Map.of("player", target.getName()));
        }
        if (!evaluateConditions(target)) {
            return Result.failure("gem.error.condition_not_met", Map.of());
        }
        ItemStack openerItem = actor == null ? null : actor.getInventory().getItemInOffHand();
        if (!bypassRequirement) {
            if (!itemMatcher.matchesOpenerItem(openerItem, opener)) {
                return Result.failure("command.open.hold_opener", Map.of("opener", opener.id()));
            }
        }
        GemState currentState = stateService.resolveState(equipment, itemDefinition);
        int slotIndex = resolveTargetSlot(itemDefinition, currentState, opener, preferredSlotIndex);
        if (slotIndex == SLOT_ALREADY_OPENED) {
            return failureWithActions(
                    target,
                    opener,
                    itemDefinition,
                    preferredSlotIndex == null ? -1 : preferredSlotIndex,
                    "command.open.slot_already_opened",
                    Map.of()
            );
        }
        if (slotIndex < 0) {
            return failureWithActions(
                    target,
                    opener,
                    itemDefinition,
                    preferredSlotIndex == null ? -1 : preferredSlotIndex,
                    preferredSlotIndex == null ? "command.open.no_available_slot" : "command.open.slot_unavailable",
                    Map.of()
            );
        }

        if (scheduling.ownsEntity(target)) {
            GemSocketOpenEvent openEvent = new GemSocketOpenEvent(UUID.randomUUID().toString(), target,
                    equipment, openerItem, opener.id(), slotIndex, itemDefinition.id());
            Bukkit.getPluginManager().callEvent(openEvent);
            if (openEvent.isCancelled()) {
                return Result.failure("gem.error.condition_not_met", Map.of());
            }
        }
        GemState nextState = currentState.withOpenedSlots(List.of(slotIndex));
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            return failureWithActions(target, opener, itemDefinition, slotIndex, "command.open.apply_failed", Map.of());
        }
        target.getInventory().setItemInMainHand(rebuilt);
        if (!bypassRequirement && opener.consumeOnSuccess()) {
            consumeOneInOffHand(actor);
        }
        Map<String, Object> placeholders = basePlaceholders(target, opener, itemDefinition, slotIndex);
        actionCoordinator.execute(target, "gem_socket_open", opener.successActions(), placeholders);
        return Result.success("command.open.success", placeholders);
    }

    private Result failureWithActions(Player target,
            SocketOpenerConfig opener,
            GemItemDefinition itemDefinition,
            int slotIndex,
            String messageKey,
            Map<String, Object> extraPlaceholders) {
        Map<String, Object> placeholders = basePlaceholders(target, opener, itemDefinition, slotIndex);
        if (extraPlaceholders != null && !extraPlaceholders.isEmpty()) {
            placeholders.putAll(extraPlaceholders);
        }
        if (target != null && opener != null) {
            actionCoordinator.execute(target, "gem_socket_open_failure", opener.failureActions(), placeholders);
        }
        return Result.failure(messageKey, placeholders);
    }

    private Map<String, Object> basePlaceholders(Player target,
            SocketOpenerConfig opener,
            GemItemDefinition itemDefinition,
            int slotIndex) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", target == null ? "" : target.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("opener", opener == null ? "" : opener.id());
        placeholders.put("item_definition_id", itemDefinition == null ? "" : itemDefinition.id());
        return placeholders;
    }

    private int resolveTargetSlot(GemItemDefinition itemDefinition,
            GemState currentState,
            SocketOpenerConfig opener,
            Integer preferredSlotIndex) {
        if (preferredSlotIndex != null) {
            GemItemDefinition.SocketSlot slot = itemDefinition.slot(preferredSlotIndex);
            if (slot == null || !opener.supportsType(slot.type())) {
                return NO_AVAILABLE_SLOT;
            }
            if (currentState.isOpened(preferredSlotIndex)) {
                return SLOT_ALREADY_OPENED;
            }
            return preferredSlotIndex;
        }
        return stateService.firstClosedSlot(itemDefinition, currentState, opener::supportsType);
    }

    private ItemStack consumeOne(ItemStack itemStack, boolean skipConsume) {
        if (itemStack == null || skipConsume) {
            return itemStack;
        }
        ItemStack updated = itemStack.clone();
        if (updated.getAmount() <= 1) {
            return null;
        }
        updated.setAmount(updated.getAmount() - 1);
        return updated;
    }

    private void consumeOneInOffHand(Player holder) {
        if (holder == null) {
            return;
        }
        ItemStack itemStack = holder.getInventory().getItemInOffHand();
        ItemStack updated = consumeOne(itemStack, false);
        holder.getInventory().setItemInOffHand(updated);
    }

    private boolean evaluateConditions(Player player) {
        var config = plugin.appConfig().condition();
        if (config.conditions().emptyGroup()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                config.conditions(),
                text -> PlaceholderRenderer.renderPapi(player, text, null, "gem_socket_open"),
                config.invalidAsFailure(),
                ConditionContext.of(player)
        );
    }
}
