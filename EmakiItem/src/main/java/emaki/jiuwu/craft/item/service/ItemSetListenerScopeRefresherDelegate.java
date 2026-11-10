package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;
import emaki.jiuwu.craft.item.model.ItemSetPieceDefinition;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;
import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;
import emaki.jiuwu.craft.item.service.ItemSetPresentationCalculator.CompileResult;
import emaki.jiuwu.craft.item.service.ItemSetPresentationCalculator.CompiledSetState;
import emaki.jiuwu.craft.item.service.ItemSetPresentationCalculator.LedgerFacts;
import emaki.jiuwu.craft.item.service.ItemSetPresentationCalculator.SetItemMutation;
import emaki.jiuwu.craft.item.service.ItemSetPresentationCalculator.SetPresentationInspection;
import emaki.jiuwu.craft.item.service.ItemSetPresentationCalculator.SetPresentationTarget;
import emaki.jiuwu.craft.item.api.event.ItemSetBonusChangeEvent;

final class ItemSetListenerScopeRefresherDelegate {

    private final ItemSetListenerScopeRefresher ownerRef;

    ItemSetListenerScopeRefresherDelegate(ItemSetListenerScopeRefresher ownerRef) {
        this.ownerRef = ownerRef;
    }

    ItemRefreshBatch createRefreshBatch(Player player) {
        return new ItemRefreshBatch(player == null ? null : player.getInventory(), ownerRef.itemOperationLedger);
    }

    int refreshEquippedSets(Player player, String trigger) {
        return ownerRef.refreshListenerScopeDetailed(
                player,
                List.of(Texts.toStringSafe(trigger)),
                Set.of(),
                true,
                true,
                Set.of(RefreshFullReason.EXPLICIT_FULL)
        ).changed();
    }

    int refreshListenerScope(Player player, String trigger, Set<Integer> dirtySlots, boolean forceFull, boolean contributionDirty) {
        return ownerRef.refreshListenerScopeDetailed(
                player,
                List.of(Texts.toStringSafe(trigger)),
                dirtySlots,
                forceFull,
                contributionDirty,
                Set.of()
        ).changed();
    }

    ItemRefreshResult refreshListenerScopeDetailed(Player player, Iterable<String> triggers, Set<Integer> dirtySlots, boolean forceFull, boolean contributionDirty, Set<RefreshFullReason> requestedFullReasons) {
        return ownerRef.refreshListenerScopeDetailed(
                player,
                triggers,
                dirtySlots,
                forceFull,
                contributionDirty,
                requestedFullReasons,
                null);
    }

    String effectiveSetTrigger(Player player, Iterable<String> triggers) {
        if (player == null) {
            return null;
        }
        AppConfig config = ownerRef.configSupplier.get();
        if (config == null) {
            if (triggers != null) {
                for (String trigger : triggers) {
                    if (Texts.isNotBlank(trigger)) {
                        return trigger;
                    }
                }
            }
            return null;
        }
        return config.setBonus().effectiveTrigger(triggers);
    }

    Set<Integer> contributionSlots(int heldSlot, int inventorySize) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        if (heldSlot >= 0 && heldSlot < inventorySize) {
            slots.add(heldSlot);
        }
        for (int slot = 36; slot <= 40; slot++) {
            if (slot < inventorySize) {
                slots.add(slot);
            }
        }
        return Collections.unmodifiableSet(slots);
    }

    Set<Integer> allSlots(int inventorySize) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        for (int slot = 0; slot < inventorySize; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    Set<Integer> unionSlots(Set<Integer> first, Set<Integer> second) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(Objects::nonNull).forEach(slots::add);
        }
        if (second != null) {
            second.stream().filter(Objects::nonNull).forEach(slots::add);
        }
        return slots;
    }

    Set<String> unionStrings(Set<String> first, Set<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (first != null) {
            values.addAll(first);
        }
        if (second != null) {
            values.addAll(second);
        }
        return values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    Set<Integer> validSlots(Set<Integer> slots, int inventorySize) {
        TreeSet<Integer> valid = new TreeSet<>();
        if (slots != null) {
            for (Integer slot : slots) {
                if (slot != null && slot >= 0 && slot < inventorySize) {
                    valid.add(slot);
                }
            }
        }
        return valid;
    }

    ItemSetRefreshPlanner.Decision forceFullForCorruptLedger(ItemSetRefreshPlanner.Decision decision) {
        LinkedHashSet<RefreshFullReason> reasons = new LinkedHashSet<>();
        if (decision != null) {
            reasons.addAll(decision.fullReasons());
        }
        reasons.add(RefreshFullReason.CACHE_INVALID);
        return new ItemSetRefreshPlanner.Decision(
                RefreshScope.FULL,
                reasons,
                decision != null && decision.cacheHit(),
                false
        );
    }

    String contributionRole(int slot, int heldSlot) {
        if (slot == heldSlot) {
            return "main_hand";
        }
        return switch (slot) {
            case 40 -> "off_hand";
            case 39 -> "helmet";
            case 38 -> "chestplate";
            case 37 -> "leggings";
            case 36 -> "boots";
            default -> "";
        };
    }

    String managedPresentationDigest(ItemMeta itemMeta, EmakiItemIdentifier.Snapshot identity, LedgerFacts ledgerFacts) {
        boolean managed = Texts.isNotBlank(identity.setId())
                || Texts.isNotBlank(identity.setSignature())
                || identity.setLoreLines() != null
                || ledgerFacts.hasSetDisplayOperations();
        if (!managed || itemMeta == null) {
            return "";
        }
        String name = ItemTextBridge.hasCustomName(itemMeta)
                ? MiniMessages.serialize(ItemTextBridge.customName(itemMeta))
                : "";
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        return SignatureUtil.stableSignature(List.of(name, lore == null ? List.of() : lore));
    }

    String contributionSignature(Map<Integer, ItemSetListenerScopeRefresher.SlotFacts> factsBySlot, Set<Integer> contributionSlots) {
        List<Map<String, Object>> contributions = new ArrayList<>();
        for (Integer slot : new TreeSet<>(contributionSlots == null ? Set.of() : contributionSlots)) {
            ItemSetListenerScopeRefresher.SlotFacts facts = factsBySlot == null ? null : factsBySlot.get(slot);
            Map<String, Object> contribution = new LinkedHashMap<>();
            contribution.put("slot", slot);
            contribution.put("role", facts == null ? "" : facts.contributionRole());
            if (facts == null) {
                contributions.add(contribution);
                continue;
            }
            EmakiItemDefinition definition = facts.definition();
            EmakiItemIdentifier.Snapshot identity = facts.identity();
            ItemSetMembership membership = facts.membership();
            contribution.put("item", identity.id());
            contribution.put("stored_definition", identity.definitionSignature());
            contribution.put("current_definition", definition == null ? "" : definition.definitionSignature());
            contribution.put("stored_update_version", identity.updateVersion());
            contribution.put("equip_slot", definition == null ? "" : definition.equipSlot());
            contribution.put("set", membership.configured() ? membership.setId() : "");
            contribution.put("piece", membership.configured() && definition != null
                    ? membership.effectivePieceId(definition.id()) : "");
            contribution.put("dynamic_set", identity.setId());
            contribution.put("dynamic_piece", identity.setPiece());
            contribution.put("dynamic_count", identity.setActiveCount());
            contribution.put("dynamic_total", identity.setTotalCount());
            contribution.put("dynamic_thresholds", identity.setActiveThresholds());
            contribution.put("dynamic_signature", identity.setSignature());
            contribution.put("legacy_marker", identity.setLoreLines());
            contribution.put("ledger", facts.ledgerFacts().operationIdentity());
            contribution.put("display", facts.managedPresentationDigest());
            contributions.add(contribution);
        }
        return SignatureUtil.stableSignature(contributions);
    }

    Map<String, Set<String>> collectEquippedPieces(Player player, String trigger, Set<Integer> contributionSlots, Map<Integer, ItemSetListenerScopeRefresher.SlotFacts> factsBySlot, EmakiItemSetLoader.Snapshot setDefinitions) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Integer slot : contributionSlots == null ? Set.<Integer>of() : contributionSlots) {
            ItemSetListenerScopeRefresher.SlotFacts facts = factsBySlot.get(slot);
            if (facts == null || facts.ledgerRead().corrupt()
                    || facts.definition() == null || !facts.membership().configured()) {
                continue;
            }
            EmakiItemDefinition definition = facts.definition();
            ItemSetMembership membership = facts.membership();
            ItemSetDefinition setDefinition = setDefinitions.get(membership.setId());
            ItemSetPieceDefinition pieceDefinition = ItemSetListenerScopeRefresher.resolveSetPiece(setDefinition, membership, definition.id());
            boolean definitionSlotMatch = EquipmentSlotMatcher.matches(facts.contributionRole(), definition.equipSlot());
            boolean setSlotMatch = pieceDefinition != null
                    && EquipmentSlotMatcher.matches(facts.contributionRole(), pieceDefinition.slot());
            boolean accepted = definitionSlotMatch && setSlotMatch;
            ownerRef.debugSetSlot(player, trigger, facts.contributionRole(), definition, membership, pieceDefinition,
                    definitionSlotMatch, setSlotMatch, accepted);
            if (accepted) {
                result.computeIfAbsent(membership.setId(), ignored -> new LinkedHashSet<>())
                        .add(pieceDefinition.pieceId());
            }
        }
        LinkedHashMap<String, Set<String>> immutable = new LinkedHashMap<>();
        result.forEach((setId, pieces) -> immutable.put(setId, Set.copyOf(pieces)));
        return immutable.isEmpty() ? Map.of() : Map.copyOf(immutable);
    }

    LinkedHashSet<String> collectVisibleSetIds(Iterable<ItemSetListenerScopeRefresher.SlotFacts> facts) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (facts == null) {
            return result;
        }
        for (ItemSetListenerScopeRefresher.SlotFacts slotFacts : facts) {
            if (slotFacts != null && slotFacts.membership().configured()) {
                result.add(slotFacts.membership().setId());
            }
        }
        return result;
    }

    String missingDefinitionKey(ItemSetListenerScopeRefresher.SlotFacts facts) {
        if (facts == null) {
            return "";
        }
        if (facts.definition() == null && Texts.isNotBlank(facts.identity().id())) {
            return "item:" + facts.identity().id();
        }
        if (facts.membership().configured()) {
            return "set:" + facts.membership().setId();
        }
        return Texts.isNotBlank(facts.identity().setId()) ? "set:" + facts.identity().setId() : "";
    }

    Map<String, EquippedSetState> equippedStates(Map<String, CompiledSetState> compiledStates) {
        LinkedHashMap<String, EquippedSetState> states = new LinkedHashMap<>();
        if (compiledStates != null) {
            compiledStates.forEach((setId, compiled) -> {
                if (compiled != null && compiled.state() != null) {
                    states.put(setId, compiled.state());
                }
            });
        }
        return states.isEmpty() ? Map.of() : Map.copyOf(states);
    }

    void debugRefreshPlan(Player player, String trigger, ItemSetRefreshPlanner.Decision decision, boolean contributionDirty, Set<Integer> dirtySlots, ItemSetListenerScopeRefresher.CaptureAccumulator capture, List<ItemSetListenerScopeRefresher.SlotPlan> plans, ItemSetListenerScopeRefresher.ApplyResult applyResult, int postCaptureScans, int postCaptureLedgers) {
        DebugLogger debugLogger = ownerRef.debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        Map<ItemSetRefreshPlanner.SlotAction, Long> planCounts = plans.stream().collect(Collectors.groupingBy(
                ItemSetListenerScopeRefresher.SlotPlan::action,
                () -> new EnumMap<>(ItemSetRefreshPlanner.SlotAction.class),
                Collectors.counting()
        ));
        debugLogger.log("set", player, "set.refresh_plan", Map.ofEntries(
                Map.entry("trigger", Texts.toStringSafe(trigger)),
                Map.entry("scope", decision.scope()),
                Map.entry("reasons", decision.fullReasons()),
                Map.entry("contribution_dirty", contributionDirty),
                Map.entry("dirty", dirtySlots == null ? Set.of() : dirtySlots),
                Map.entry("plans", planCounts),
                Map.entry("scanned", capture.scannedSlots()),
                Map.entry("ledger_decodes", capture.ledgerDecodes()),
                Map.entry("post_scans", postCaptureScans),
                Map.entry("post_ledgers", postCaptureLedgers),
                Map.entry("changed", applyResult.changed()),
                Map.entry("conflicts", applyResult.conflicts())
        ));
    }

    void clearCachedState(UUID uuid) {
        if (uuid != null) {
            ownerRef.lastActiveCounts.remove(uuid);
            ownerRef.listenerCaches.remove(uuid);
        }
    }

    void invalidateCachedState(UUID uuid) {
        if (uuid != null) {
            ownerRef.listenerCaches.remove(uuid);
        }
    }

    void debugSetSlot(Player player, String trigger, String actualSlot, EmakiItemDefinition definition, ItemSetMembership membership, ItemSetPieceDefinition pieceDefinition, boolean definitionSlotMatch, boolean setSlotMatch, boolean accepted) {
        DebugLogger debugLogger = ownerRef.debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.log("set", player, "set.slot_gate", Map.of(
                "trigger", Texts.toStringSafe(trigger),
                "item", definition.id(),
                "actual", Texts.toStringSafe(actualSlot),
                "item_slot", Texts.toStringSafe(definition.equipSlot()),
                "set", membership.setId(),
                "piece", pieceDefinition == null ? "<unresolved>" : pieceDefinition.pieceId(),
                "set_slot", pieceDefinition == null ? "<unresolved>" : pieceDefinition.slot(),
                "item_match", definitionSlotMatch,
                "set_match", setSlotMatch,
                "accepted", accepted
        ));
    }

    void debugSetWrite(Player player, String trigger, String slot, String itemId, String operation, boolean committed) {
        DebugLogger debugLogger = ownerRef.debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.log("set", player, "set.write", Map.of(
                "trigger", Texts.toStringSafe(trigger),
                "slot", Texts.toStringSafe(slot),
                "item", Texts.toStringSafe(itemId),
                "operation", Texts.toStringSafe(operation),
                "committed", committed,
                "owner", ownerRef.scheduling != null && ownerRef.scheduling.ownsEntity(player),
                "thread", Thread.currentThread().getName()
        ));
    }

    static ItemSetPieceDefinition resolveSetPiece(ItemSetDefinition setDefinition, ItemSetMembership membership, String itemId) {
        if (setDefinition == null || membership == null || !membership.configured()) {
            return null;
        }
        if (Texts.isNotBlank(membership.pieceId())) {
            return setDefinition.pieces().get(membership.pieceId());
        }
        ItemSetPieceDefinition matched = null;
        for (ItemSetPieceDefinition pieceDefinition : setDefinition.pieces().values()) {
            if (pieceDefinition == null || !Texts.normalizeId(itemId).equals(Texts.normalizeId(pieceDefinition.itemId()))) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = pieceDefinition;
        }
        return matched;
    }

    boolean writeInventoryIfUnchanged(PlayerInventory inventory, int slot, ItemStack expected, ItemStack updated) {
        ItemStack current = inventory.getItem(slot);
        if (!ItemSetListenerScopeRefresherSupport.sameItem(current, expected)) {
            return false;
        }
        inventory.setItem(slot, updated);
        return true;
    }

}
