package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
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

public final class EmakiItemSetService {

    private static final String SET_DISPLAY_NAMESPACE = "emakiitem:set_display";
    private static final NamespacedKey OPERATIONS_KEY = java.util.Objects.requireNonNull(
            NamespacedKey.fromString("emaki:item.operations"));

    private final EmakiItemLoader itemLoader;
    private final EmakiItemSetLoader setLoader;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final EmakiItemPdcWriter pdcWriter;
    private final ItemSetLoreRenderer loreRenderer;
    private final ItemOperationLedger itemOperationLedger;
    private final Supplier<AppConfig> configSupplier;
    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final Logger logger;
    private final ThreadOwnership threadOwnership;
    private final Set<String> warnedMissingSetDefinitions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ItemSetRefreshPlanner refreshPlanner = new ItemSetRefreshPlanner();

    private final Map<java.util.UUID, Map<String, Integer>> lastActiveCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<java.util.UUID, ListenerSetCache> listenerCaches = new java.util.concurrent.ConcurrentHashMap<>();

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, () -> null, null, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier,
                               Supplier<DebugLogger> debugLoggerSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, debugLoggerSupplier, null, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier,
                               Supplier<DebugLogger> debugLoggerSupplier,
                               Logger logger) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier,
                debugLoggerSupplier, logger, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier,
                               Supplier<DebugLogger> debugLoggerSupplier,
                               Logger logger,
                               ThreadOwnership threadOwnership) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier,
                debugLoggerSupplier, logger, null, threadOwnership);
    }

    EmakiItemSetService(EmakiItemLoader itemLoader,
                        EmakiItemSetLoader setLoader,
                        EmakiItemFactory itemFactory,
                        EmakiItemIdentifier identifier,
                        EmakiItemPdcWriter pdcWriter,
                        ItemSetLoreRenderer loreRenderer,
                        Supplier<AppConfig> configSupplier,
                        ItemOperationLedger itemOperationLedger) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier,
                () -> null, null, itemOperationLedger, null);
    }

    private EmakiItemSetService(EmakiItemLoader itemLoader,
                                EmakiItemSetLoader setLoader,
                                EmakiItemFactory itemFactory,
                                EmakiItemIdentifier identifier,
                                EmakiItemPdcWriter pdcWriter,
                                ItemSetLoreRenderer loreRenderer,
                                Supplier<AppConfig> configSupplier,
                                Supplier<DebugLogger> debugLoggerSupplier,
                                Logger logger,
                                ItemOperationLedger itemOperationLedger,
                                ThreadOwnership threadOwnership) {
        this.itemLoader = itemLoader;
        this.setLoader = setLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.loreRenderer = loreRenderer;
        this.configSupplier = configSupplier;
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
        this.logger = logger;
        this.threadOwnership = threadOwnership;
        this.itemOperationLedger = itemOperationLedger == null ? new ItemOperationLedger(this.debugLoggerSupplier) : itemOperationLedger;
    }

    public int refreshEquippedSets(Player player, String trigger) {
        return refreshListenerScopeDetailed(
                player,
                List.of(Texts.toStringSafe(trigger)),
                Set.of(),
                true,
                true,
                Set.of(RefreshFullReason.EXPLICIT_FULL)
        ).changed();
    }

    public int refreshListenerScope(Player player,
                                    String trigger,
                                    Set<Integer> dirtySlots,
                                    boolean forceFull,
                                    boolean contributionDirty) {
        return refreshListenerScopeDetailed(
                player,
                List.of(Texts.toStringSafe(trigger)),
                dirtySlots,
                forceFull,
                contributionDirty,
                Set.of()
        ).changed();
    }

    public ItemRefreshResult refreshListenerScopeDetailed(Player player,
                                                          Iterable<String> triggers,
                                                          Set<Integer> dirtySlots,
                                                          boolean forceFull,
                                                          boolean contributionDirty,
                                                          Set<RefreshFullReason> requestedFullReasons) {
        long started = System.nanoTime();
        RefreshScope requestedScope = forceFull ? RefreshScope.FULL
                : dirtySlots == null || dirtySlots.isEmpty() ? RefreshScope.SKIP : RefreshScope.LOCAL;
        String effectiveTrigger = effectiveSetTrigger(player, triggers);
        if (player == null || effectiveTrigger == null) {
            ListenerSetCache existing = player == null ? null : listenerCaches.get(player.getUniqueId());
            return new ItemRefreshResult(requestedScope, RefreshScope.SKIP, RefreshScope.SKIP,
                    requestedFullReasons, existing != null, existing != null && existing.valid(),
                    0, 0, 0, 0, 0, 0, "", System.nanoTime() - started);
        }

        PlayerInventory inventory = player.getInventory();
        int inventorySize = inventory.getSize();
        int heldSlot = inventory.getHeldItemSlot();
        Set<Integer> contributionSlots = contributionSlots(heldSlot, inventorySize);
        boolean dirtyScopeComplete = !forceFull
                && dirtySlots != null
                && !dirtySlots.isEmpty()
                && dirtySlots.stream().allMatch(slot -> slot != null && slot >= 0 && slot < inventorySize);

        EmakiItemLoader.Snapshot itemDefinitions = itemLoader.snapshot();
        EmakiItemSetLoader.Snapshot setDefinitions = setLoader.snapshot();
        ListenerSetCache cached = listenerCaches.get(player.getUniqueId());
        ItemSetRefreshPlanner.Decision decision = refreshPlanner.decideInitial(
                new ItemSetRefreshPlanner.Request(
                        forceFull,
                        dirtyScopeComplete,
                        itemDefinitions.generation(),
                        setDefinitions.generation(),
                        requestedFullReasons
                ),
                cached == null ? null : new ItemSetRefreshPlanner.CacheView(
                        cached.valid(), cached.itemGeneration(), cached.setGeneration())
        );

        CaptureAccumulator capture = new CaptureAccumulator();
        Set<Integer> initialSlots = decision.scope() == RefreshScope.FULL
                ? allSlots(inventorySize)
                : unionSlots(dirtySlots, contributionSlots);
        captureSlots(inventory, heldSlot, initialSlots, itemDefinitions, capture, false);
        if (capture.hasCorruptLedger()) {
            decision = forceFullForCorruptLedger(decision);
            captureSlots(inventory, heldSlot, allSlots(inventorySize), itemDefinitions, capture, false);
        }
        String contributionSignature = contributionSignature(capture.facts(), contributionSlots);
        if (decision.scope() == RefreshScope.LOCAL) {
            ListenerSetCache localCache = java.util.Objects.requireNonNull(cached, "local refresh cache");
            decision = refreshPlanner.decideContribution(
                    decision,
                    localCache.contributionSignature(),
                    contributionSignature
            );
            if (decision.scope() == RefreshScope.FULL) {
                captureSlots(inventory, heldSlot, allSlots(inventorySize), itemDefinitions, capture, false);
                if (capture.hasCorruptLedger()) {
                    decision = forceFullForCorruptLedger(decision);
                }
                contributionSignature = contributionSignature(capture.facts(), contributionSlots);
            }
        }

        Map<String, Set<String>> equippedPiecesBySet = collectEquippedPieces(
                player,
                effectiveTrigger,
                contributionSlots,
                capture.facts(),
                setDefinitions
        );
        LinkedHashMap<String, CompiledSetState> compiledStates = new LinkedHashMap<>();
        if (decision.scope() == RefreshScope.LOCAL) {
            compiledStates.putAll(java.util.Objects.requireNonNull(cached, "local refresh cache").compiledStates());
        }
        LinkedHashSet<String> visibleSetIds = collectVisibleSetIds(capture.facts().values());
        if (decision.scope() == RefreshScope.LOCAL) {
            visibleSetIds.clear();
            for (Integer slot : dirtySlots == null ? Set.<Integer>of() : dirtySlots) {
                SlotFacts facts = capture.facts().get(slot);
                if (facts != null && facts.membership().configured()) {
                    visibleSetIds.add(facts.membership().setId());
                }
            }
        }
        CompileResult compileResult = compileStates(
                visibleSetIds,
                equippedPiecesBySet,
                setDefinitions,
                compiledStates
        );
        compiledStates = new LinkedHashMap<>(compileResult.compiledStates());

        Set<Integer> targetSlots = decision.scope() == RefreshScope.FULL
                ? allSlots(inventorySize)
                : validSlots(dirtySlots, inventorySize);
        List<SlotPlan> plans = planSlots(targetSlots, capture.facts(), compiledStates);
        ApplyResult applyResult = applyPlans(player, inventory, effectiveTrigger, plans, contributionSlots);

        LinkedHashSet<String> missingDefinitions = new LinkedHashSet<>(compileResult.missingDefinitions());
        missingDefinitions.addAll(applyResult.missingDefinitions());
        LinkedHashSet<String> missingSetIds = new LinkedHashSet<>(compileResult.missingSetIds());
        missingSetIds.addAll(applyResult.missingSetIds());

        int scannedBeforeCacheRebuild = capture.scannedSlots();
        int ledgerBeforeCacheRebuild = capture.ledgerDecodes();
        String finalContributionSignature = contributionSignature;
        Map<String, Set<String>> finalEquippedPiecesBySet = equippedPiecesBySet;
        LinkedHashMap<String, CompiledSetState> finalCompiledStates = new LinkedHashMap<>(compiledStates);
        int totalSetCompiles = compileResult.compiles();
        if (applyResult.contributionChanged() || applyResult.conflicts() > 0) {
            captureSlots(inventory, heldSlot, contributionSlots, itemDefinitions, capture, true);
            if (!capture.hasCorruptLedger()) {
                finalContributionSignature = contributionSignature(capture.facts(), contributionSlots);
                finalEquippedPiecesBySet = collectEquippedPieces(
                        player,
                        effectiveTrigger,
                        contributionSlots,
                        capture.facts(),
                        setDefinitions
                );
                LinkedHashSet<String> finalSetIds = collectVisibleSetIds(capture.facts().values());
                finalSetIds.addAll(finalCompiledStates.keySet());
                finalSetIds.addAll(finalEquippedPiecesBySet.keySet());
                finalSetIds.addAll(lastActiveCounts.getOrDefault(player.getUniqueId(), Map.of()).keySet());
                CompileResult rebuilt = compileStates(
                        finalSetIds,
                        finalEquippedPiecesBySet,
                        setDefinitions,
                        Map.of()
                );
                finalCompiledStates = new LinkedHashMap<>(rebuilt.compiledStates());
                missingDefinitions.addAll(rebuilt.missingDefinitions());
                missingSetIds.addAll(rebuilt.missingSetIds());
                totalSetCompiles += rebuilt.compiles();
            }
        }
        warnMissingDefinitions(player, effectiveTrigger, missingDefinitions);

        boolean stateReliable = !capture.hasCorruptLedger();
        boolean cacheValid = stateReliable && applyResult.conflicts() == 0;
        if (cacheValid) {
            listenerCaches.put(player.getUniqueId(), new ListenerSetCache(
                    true,
                    itemDefinitions.generation(),
                    setDefinitions.generation(),
                    finalContributionSignature,
                    finalCompiledStates,
                    finalEquippedPiecesBySet
            ));
        } else {
            listenerCaches.remove(player.getUniqueId());
        }
        if (stateReliable) {
            fireSetBonusChangeEvents(
                    player,
                    equippedStates(finalCompiledStates),
                    missingSetIds,
                    effectiveTrigger
            );
        }

        LinkedHashSet<RefreshFullReason> fullReasons = new LinkedHashSet<>(decision.fullReasons());
        if (applyResult.conflicts() > 0) {
            fullReasons.add(RefreshFullReason.COMPARE_CONFLICT);
        }
        if (!cacheValid) {
            fullReasons.add(RefreshFullReason.CACHE_INVALID);
        }
        int postCaptureScans = capture.scannedSlots() - scannedBeforeCacheRebuild;
        int postCaptureLedgers = capture.ledgerDecodes() - ledgerBeforeCacheRebuild;
        debugRefreshPlan(player, effectiveTrigger, decision, contributionDirty, dirtySlots,
                capture, plans, applyResult, postCaptureScans, postCaptureLedgers);
        return new ItemRefreshResult(
                requestedScope,
                RefreshScope.SKIP,
                decision.scope(),
                fullReasons,
                decision.cacheHit(),
                cacheValid,
                0,
                capture.scannedSlots(),
                applyResult.changed(),
                applyResult.conflicts(),
                capture.ledgerDecodes(),
                totalSetCompiles,
                effectiveTrigger,
                System.nanoTime() - started
        );
    }

    private String effectiveSetTrigger(Player player, Iterable<String> triggers) {
        if (player == null) {
            return null;
        }
        AppConfig config = configSupplier.get();
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

    private Set<Integer> contributionSlots(int heldSlot, int inventorySize) {
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

    private Set<Integer> allSlots(int inventorySize) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        for (int slot = 0; slot < inventorySize; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    private Set<Integer> unionSlots(Set<Integer> first, Set<Integer> second) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(java.util.Objects::nonNull).forEach(slots::add);
        }
        if (second != null) {
            second.stream().filter(java.util.Objects::nonNull).forEach(slots::add);
        }
        return slots;
    }

    private Set<String> unionStrings(Set<String> first, Set<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (first != null) {
            values.addAll(first);
        }
        if (second != null) {
            values.addAll(second);
        }
        return values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    private Set<Integer> validSlots(Set<Integer> slots, int inventorySize) {
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

    private ItemSetRefreshPlanner.Decision forceFullForCorruptLedger(ItemSetRefreshPlanner.Decision decision) {
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

    private void captureSlots(PlayerInventory inventory,
                              int heldSlot,
                              Set<Integer> slots,
                              EmakiItemLoader.Snapshot itemDefinitions,
                              CaptureAccumulator accumulator,
                              boolean replace) {
        if (inventory == null || slots == null || accumulator == null) {
            return;
        }
        for (Integer boxedSlot : new TreeSet<>(slots)) {
            if (boxedSlot == null) {
                continue;
            }
            int slot = boxedSlot;
            if (slot < 0 || slot >= inventory.getSize() || !replace && accumulator.facts().containsKey(slot)) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            ItemStack expected = cloneItem(current);
            ItemMeta itemMeta = expected == null ? null : expected.getItemMeta();
            EmakiItemIdentifier.Snapshot identity = identifier.snapshot(itemMeta);
            EmakiItemDefinition definition = Texts.isBlank(identity.id()) ? null : itemDefinitions.get(identity.id());
            ItemSetMembership membership = definition == null ? ItemSetMembership.empty() : definition.setMembership();
            ItemOperationLedger.ReadResult ledgerRead;
            if (expected == null || expected.getType().isAir() || !operationsFieldPresent(expected)) {
                ledgerRead = ItemOperationLedger.ReadResult.absent();
            } else {
                ledgerRead = itemOperationLedger.read(expected);
                accumulator.incrementLedgerDecodes();
            }
            LedgerFacts ledgerFacts = LedgerFacts.from(ledgerRead);
            accumulator.put(new SlotFacts(
                    slot,
                    contributionRole(slot, heldSlot),
                    expected,
                    identity,
                    definition,
                    membership,
                    ledgerRead,
                    ledgerFacts,
                    managedPresentationDigest(itemMeta, identity, ledgerFacts)
            ));
            accumulator.incrementScannedSlots();
        }
    }

    private String contributionRole(int slot, int heldSlot) {
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

    private String managedPresentationDigest(ItemMeta itemMeta,
                                             EmakiItemIdentifier.Snapshot identity,
                                             LedgerFacts ledgerFacts) {
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

    private String contributionSignature(Map<Integer, SlotFacts> factsBySlot, Set<Integer> contributionSlots) {
        List<Map<String, Object>> contributions = new ArrayList<>();
        for (Integer slot : new TreeSet<>(contributionSlots == null ? Set.of() : contributionSlots)) {
            SlotFacts facts = factsBySlot == null ? null : factsBySlot.get(slot);
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

    private Map<String, Set<String>> collectEquippedPieces(Player player,
                                                           String trigger,
                                                           Set<Integer> contributionSlots,
                                                           Map<Integer, SlotFacts> factsBySlot,
                                                           EmakiItemSetLoader.Snapshot setDefinitions) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Integer slot : contributionSlots == null ? Set.<Integer>of() : contributionSlots) {
            SlotFacts facts = factsBySlot.get(slot);
            if (facts == null || facts.ledgerRead().corrupt()
                    || facts.definition() == null || !facts.membership().configured()) {
                continue;
            }
            EmakiItemDefinition definition = facts.definition();
            ItemSetMembership membership = facts.membership();
            ItemSetDefinition setDefinition = setDefinitions.get(membership.setId());
            ItemSetPieceDefinition pieceDefinition = resolveSetPiece(setDefinition, membership, definition.id());
            boolean definitionSlotMatch = EquipmentSlotMatcher.matches(facts.contributionRole(), definition.equipSlot());
            boolean setSlotMatch = pieceDefinition != null
                    && EquipmentSlotMatcher.matches(facts.contributionRole(), pieceDefinition.slot());
            boolean accepted = definitionSlotMatch && setSlotMatch;
            debugSetSlot(player, trigger, facts.contributionRole(), definition, membership, pieceDefinition,
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

    private LinkedHashSet<String> collectVisibleSetIds(Iterable<SlotFacts> facts) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (facts == null) {
            return result;
        }
        for (SlotFacts slotFacts : facts) {
            if (slotFacts != null && slotFacts.membership().configured()) {
                result.add(slotFacts.membership().setId());
            }
        }
        return result;
    }

    private CompileResult compileStates(Set<String> setIds,
                                        Map<String, Set<String>> equippedPiecesBySet,
                                        EmakiItemSetLoader.Snapshot setDefinitions,
                                        Map<String, CompiledSetState> existingStates) {
        LinkedHashMap<String, CompiledSetState> compiled = new LinkedHashMap<>();
        if (existingStates != null) {
            compiled.putAll(existingStates);
        }
        LinkedHashSet<String> missingDefinitions = new LinkedHashSet<>();
        LinkedHashSet<String> missingSetIds = new LinkedHashSet<>();
        int compiles = 0;
        for (String setId : setIds == null ? Set.<String>of() : setIds) {
            if (Texts.isBlank(setId) || compiled.containsKey(setId)) {
                continue;
            }
            ItemSetDefinition definition = setDefinitions.get(setId);
            if (definition == null) {
                missingDefinitions.add("set:" + Texts.normalizeId(setId));
                missingSetIds.add(Texts.normalizeId(setId));
                continue;
            }
            EquippedSetState state = new EquippedSetState(
                    definition,
                    equippedPiecesBySet == null ? Set.of() : equippedPiecesBySet.getOrDefault(setId, Set.of())
            );
            compiled.put(setId, compileState(state));
            compiles++;
        }
        return new CompileResult(compiled, missingDefinitions, missingSetIds, compiles);
    }

    private CompiledSetState compileState(EquippedSetState state) {
        List<ItemSetThreshold> activeThresholds = state.activeThresholds();
        List<Integer> activeThresholdNumbers = activeThresholds.stream()
                .map(ItemSetThreshold::requiredPieces)
                .toList();
        Map<String, Double> attributes = new LinkedHashMap<>();
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        List<Object> nameActions = new ArrayList<>();
        List<Object> loreActions = new ArrayList<>();
        for (ItemSetThreshold threshold : activeThresholds) {
            threshold.attributes().forEach((key, value) -> attributes.merge(key, value, Double::sum));
            skills.addAll(threshold.skills());
            appendActionValues(nameActions, threshold.nameActions());
            appendActionValues(loreActions, threshold.loreActions());
        }
        List<String> setLore = canonicalLoreLines(loreRenderer.render(state));
        String stateSignature = SignatureUtil.stableSignature(List.of(
                state.definition().id(),
                state.definition().displayName(),
                state.activeCount(),
                state.equippedPieces().stream().sorted().toList(),
                activeThresholdNumbers,
                setLore,
                nameActions,
                loreActions,
                attributes,
                skills
        ));
        return new CompiledSetState(
                state,
                setLore,
                activeThresholdNumbers,
                nameActions.isEmpty() ? List.of() : List.copyOf(nameActions),
                loreActions.isEmpty() ? List.of() : List.copyOf(loreActions),
                attributes.isEmpty() ? Map.of() : Map.copyOf(attributes),
                skills.isEmpty() ? List.of() : List.copyOf(skills),
                stateSignature
        );
    }

    private void appendActionValues(List<Object> sink, Object raw) {
        if (sink == null || raw == null) {
            return;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value != null) {
                    sink.add(value);
                }
            }
            return;
        }
        sink.add(raw);
    }

    private List<SlotPlan> planSlots(Set<Integer> targetSlots,
                                     Map<Integer, SlotFacts> factsBySlot,
                                     Map<String, CompiledSetState> compiledStates) {
        List<SlotPlan> plans = new ArrayList<>();
        for (Integer slot : new TreeSet<>(targetSlots == null ? Set.of() : targetSlots)) {
            SlotFacts facts = factsBySlot.get(slot);
            if (facts == null || facts.expected() == null || facts.expected().getType().isAir()) {
                continue;
            }
            if (facts.ledgerRead().status() == ItemOperationLedger.ReadStatus.CORRUPT) {
                plans.add(new SlotPlan(slot, ItemSetRefreshPlanner.SlotAction.NO_OP, facts, null));
                continue;
            }
            boolean membershipConfigured = facts.membership().configured();
            CompiledSetState compiledState = membershipConfigured
                    ? compiledStates.get(facts.membership().setId())
                    : null;
            ItemSetRefreshPlanner.SlotAction action = refreshPlanner.planSlot(
                    Texts.isNotBlank(facts.identity().id()),
                    facts.definition() != null,
                    membershipConfigured,
                    compiledState != null,
                    facts.hasSetPresentation()
            );
            plans.add(new SlotPlan(slot, action, facts, compiledState));
        }
        return plans.isEmpty() ? List.of() : List.copyOf(plans);
    }

    private ApplyResult applyPlans(Player player,
                                   PlayerInventory inventory,
                                   String trigger,
                                   List<SlotPlan> plans,
                                   Set<Integer> contributionSlots) {
        int changed = 0;
        int conflicts = 0;
        boolean contributionChanged = false;
        LinkedHashSet<String> missingDefinitions = new LinkedHashSet<>();
        LinkedHashSet<String> missingSetIds = new LinkedHashSet<>();
        for (SlotPlan plan : plans == null ? List.<SlotPlan>of() : plans) {
            SlotFacts facts = plan.facts();
            if (plan.action() == ItemSetRefreshPlanner.SlotAction.NO_OP) {
                continue;
            }
            if (plan.action() == ItemSetRefreshPlanner.SlotAction.PRESERVE_MISSING_DEFINITION) {
                missingDefinitions.add(missingDefinitionKey(facts));
                if (facts.membership().configured()) {
                    missingSetIds.add(facts.membership().setId());
                } else if (Texts.isNotBlank(facts.identity().setId())) {
                    missingSetIds.add(facts.identity().setId());
                }
                continue;
            }
            ItemStack expected = facts.expected();
            SetItemMutation mutation;
            String operation;
            SetPresentationTarget target = null;
            SetPresentationInspection before = null;
            if (plan.action() == ItemSetRefreshPlanner.SlotAction.APPLY) {
                CompiledSetState compiledState = plan.compiledState();
                target = buildPresentationTarget(facts.definition(), compiledState);
                before = inspectSetPresentation(
                        facts.expected(),
                        facts.ledgerRead(),
                        facts.identity(),
                        facts.ledgerFacts(),
                        facts.definition(),
                        facts.membership(),
                        compiledState.state(),
                        target
                );
                if (before.current()) {
                    continue;
                }
                mutation = renderSetItem(
                        expected.clone(),
                        facts.definition(),
                        facts.membership(),
                        compiledState.state(),
                        target,
                        before,
                        facts.ledgerRead(),
                        facts.ledgerFacts(),
                        facts.identity()
                );
                operation = "apply";
            } else {
                mutation = clearSetPresentation(
                        expected.clone(), facts.definition(), facts.ledgerRead(), facts.identity());
                operation = "clear";
            }
            ItemStack updated = mutation.itemStack();
            if (!mutation.success() || sameItem(expected, updated)) {
                continue;
            }
            boolean committed = writeInventoryIfUnchanged(inventory, plan.slot(), expected, updated);
            if (!committed) {
                conflicts++;
                if (plan.action() == ItemSetRefreshPlanner.SlotAction.APPLY) {
                    debugSetLore(player, trigger, "slot_" + plan.slot(), facts.definition().id(),
                            facts.identity().setSignature(), facts.identity().setLoreLines(), loreSize(expected), updated,
                            false, before, facts.membership().setId(), target);
                } else {
                    debugSetWrite(player, trigger, "slot_" + plan.slot(), facts.identity().id(), operation, false);
                }
                continue;
            }
            changed++;
            contributionChanged |= contributionSlots.contains(plan.slot());
            if (plan.action() == ItemSetRefreshPlanner.SlotAction.APPLY) {
                debugSetLore(player, trigger, "slot_" + plan.slot(), facts.definition().id(),
                        facts.identity().setSignature(), facts.identity().setLoreLines(), loreSize(expected), updated,
                        true, before, facts.membership().setId(), target);
            } else {
                debugSetWrite(player, trigger, "slot_" + plan.slot(), facts.identity().id(), operation, true);
            }
        }
        missingDefinitions.remove("");
        missingSetIds.remove("");
        return new ApplyResult(changed, conflicts, contributionChanged, missingDefinitions, missingSetIds);
    }

    private String missingDefinitionKey(SlotFacts facts) {
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

    private void warnMissingDefinitions(Player player, String trigger, Set<String> missingDefinitions) {
        if (missingDefinitions == null || missingDefinitions.isEmpty()) {
            return;
        }
        List<String> newlyMissing = missingDefinitions.stream()
                .filter(Texts::isNotBlank)
                .filter(warnedMissingSetDefinitions::add)
                .sorted()
                .toList();
        if (newlyMissing.isEmpty()) {
            return;
        }
        if (logger != null) {
            List<String> sample = newlyMissing.stream().limit(10).toList();
            logger.warning("Missing EmakiItem definitions detected during set refresh; preserving existing presentation. "
                    + "count=" + newlyMissing.size() + ", definitions=" + sample
                    + (newlyMissing.size() > sample.size() ? ", additional=" + (newlyMissing.size() - sample.size()) : ""));
        }
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger != null && debugLogger.shouldLog("set", player)) {
            debugLogger.log("set", player, "set.missing_definitions", Map.of(
                    "trigger", Texts.toStringSafe(trigger),
                    "definitions", newlyMissing
            ));
        }
    }

    private Map<String, EquippedSetState> equippedStates(Map<String, CompiledSetState> compiledStates) {
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

    private void debugRefreshPlan(Player player,
                                  String trigger,
                                  ItemSetRefreshPlanner.Decision decision,
                                  boolean contributionDirty,
                                  Set<Integer> dirtySlots,
                                  CaptureAccumulator capture,
                                  List<SlotPlan> plans,
                                  ApplyResult applyResult,
                                  int postCaptureScans,
                                  int postCaptureLedgers) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        Map<ItemSetRefreshPlanner.SlotAction, Long> planCounts = plans.stream().collect(java.util.stream.Collectors.groupingBy(
                SlotPlan::action,
                () -> new java.util.EnumMap<>(ItemSetRefreshPlanner.SlotAction.class),
                java.util.stream.Collectors.counting()
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


    private void fireSetBonusChangeEvents(Player player,
                                          Map<String, EquippedSetState> states,
                                          Set<String> missingDefinitions,
                                          String trigger) {
        if (player == null || threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
            return;
        }
        java.util.UUID uuid = player.getUniqueId();
        Map<String, Integer> previous = lastActiveCounts.getOrDefault(uuid, Map.of());
        Map<String, Integer> current = new LinkedHashMap<>();
        for (Map.Entry<String, EquippedSetState> entry : states.entrySet()) {
            EquippedSetState state = entry.getValue();
            if (state != null && state.activeCount() > 0) {
                current.put(entry.getKey(), state.activeCount());
            }
        }
        for (String setId : missingDefinitions == null ? Set.<String>of() : missingDefinitions) {
            Integer preservedCount = previous.get(setId);
            if (preservedCount != null && preservedCount > 0) {
                current.putIfAbsent(setId, preservedCount);
            }
        }

        boolean entityOwned = threadOwnership != null && threadOwnership.isEntityOwned(player);
        Set<String> setIds = new LinkedHashSet<>(previous.keySet());
        setIds.addAll(current.keySet());
        for (String setId : setIds) {
            int oldCount = previous.getOrDefault(setId, 0);
            int newCount = current.getOrDefault(setId, 0);
            if (oldCount == newCount) {
                continue;
            }
            if (entityOwned) {
                EquippedSetState state = states.get(setId);
                int totalPieces = state != null && state.definition() != null ? state.definition().totalPieces() : 0;
                List<Integer> activeThresholds = state == null
                        ? List.of()
                        : state.activeThresholds().stream().map(ItemSetThreshold::requiredPieces).toList();
                org.bukkit.Bukkit.getPluginManager().callEvent(new emaki.jiuwu.craft.item.api.event.ItemSetBonusChangeEvent(
                        player, setId, oldCount, newCount, totalPieces, activeThresholds, trigger));
            }
        }
        if (current.isEmpty()) {
            lastActiveCounts.remove(uuid);
        } else {
            lastActiveCounts.put(uuid, current);
        }
    }


    public void clearCachedState(java.util.UUID uuid) {
        if (uuid != null) {
            lastActiveCounts.remove(uuid);
            listenerCaches.remove(uuid);
        }
    }

    public void invalidateCachedState(java.util.UUID uuid) {
        if (uuid != null) {
            listenerCaches.remove(uuid);
        }
    }

    public void clearAllCachedState() {
        lastActiveCounts.clear();
        listenerCaches.clear();
        warnedMissingSetDefinitions.clear();
    }

    ItemStack clearSetPresentation(ItemStack itemStack, EmakiItemDefinition definition) {
        EmakiItemIdentifier.Snapshot identity = identifier.snapshot(itemStack);
        ItemOperationLedger.ReadResult ledgerRead = itemOperationLedger.read(itemStack);
        return clearSetPresentation(itemStack, definition, ledgerRead, identity).itemStack();
    }

    private SetItemMutation clearSetPresentation(ItemStack itemStack,
                                                 EmakiItemDefinition definition,
                                                 ItemOperationLedger.ReadResult ledgerRead,
                                                 EmakiItemIdentifier.Snapshot identity) {
        ItemStack original = itemStack == null ? null : itemStack.clone();
        if (itemStack == null || ledgerRead == null || ledgerRead.corrupt()) {
            return SetItemMutation.failure(original, ledgerRead);
        }
        Integer legacyLoreLines = identity.setLoreLines();
        boolean staticOperationPresent = ledgerRead.entries().stream().anyMatch(entry -> entry != null
                && SET_DISPLAY_NAMESPACE.equals(entry.sourceNamespace())
                && entry.operationId().startsWith("emakiitem:set_static_lore:"));
        ItemOperationLedger.UpdateResult reverted = revertNamespaceOperations(
                itemStack, ledgerRead, SET_DISPLAY_NAMESPACE);
        if (!reverted.success() || hasNamespaceOperation(reverted.entries(), SET_DISPLAY_NAMESPACE)) {
            return SetItemMutation.failure(original, ledgerRead);
        }
        if (legacyLoreLines != null && !staticOperationPresent
                && !migrateLegacyStaticLore(itemStack, List.of(), legacyLoreLines)) {
            return SetItemMutation.failure(original, ledgerRead);
        }
        pdcWriter.clearDynamicSet(itemStack, definition);
        return SetItemMutation.success(itemStack, reverted.readResult());
    }

    private boolean clearLegacyLoreMarker(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        identifier.clearSetLoreLines(itemMeta);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    private boolean migrateLegacyStaticLore(ItemStack itemStack,
                                            List<String> expectedSetLore,
                                            Integer legacyLoreLines) {
        if (legacyLoreLines == null) {
            return true;
        }
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        int markerLines = Math.max(0, legacyLoreLines);
        List<String> currentLore = loreLines(itemStack);
        List<String> canonicalSetLore = canonicalLoreLines(expectedSetLore);
        List<String> migratedLore = new ArrayList<>(currentLore);
        if (markerLines > 0) {
            if (canonicalSetLore.isEmpty()) {
                return false;
            }
            List<String> expectedBlock;
            if (markerLines == canonicalSetLore.size()) {
                expectedBlock = canonicalSetLore;
            } else if (markerLines == canonicalSetLore.size() + 1) {
                expectedBlock = new ArrayList<>(canonicalSetLore.size() + 1);
                expectedBlock.add("");
                expectedBlock.addAll(canonicalSetLore);
            } else {
                return false;
            }
            int matchedStart = uniqueBlockStart(migratedLore, expectedBlock);
            if (matchedStart < 0) {
                return false;
            }
            migratedLore.subList(matchedStart, matchedStart + expectedBlock.size()).clear();
        }
        ItemTextBridge.setLoreLines(itemMeta, migratedLore);
        identifier.clearSetLoreLines(itemMeta);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    static List<String> stripTrailingSetLoreBlocks(List<String> lore, List<String> setLore) {
        List<String> result = lore == null || lore.isEmpty() ? new ArrayList<>() : new ArrayList<>(lore);
        List<String> canonicalSetLore = canonicalLoreLines(setLore);
        if (canonicalSetLore.isEmpty()) {
            return result;
        }
        while (endsWith(result, canonicalSetLore)) {
            result.subList(result.size() - canonicalSetLore.size(), result.size()).clear();
            removeSeparatorBefore(result, result.size());
        }
        return result;
    }

    private static int uniqueBlockStart(List<String> lines, List<String> canonicalBlock) {
        int matchedStart = -1;
        if (lines == null || canonicalBlock == null || canonicalBlock.isEmpty()) {
            return matchedStart;
        }
        for (int start = 0; start <= lines.size() - canonicalBlock.size(); start++) {
            if (!matchesAt(lines, canonicalBlock, start)) {
                continue;
            }
            if (matchedStart >= 0) {
                return -1;
            }
            matchedStart = start;
        }
        return matchedStart;
    }

    private static boolean endsWith(List<String> lines, List<String> suffix) {
        return lines != null
                && suffix != null
                && !suffix.isEmpty()
                && lines.size() >= suffix.size()
                && matchesAt(lines, suffix, lines.size() - suffix.size());
    }

    private static boolean matchesAt(List<String> lines, List<String> canonicalBlock, int start) {
        if (lines == null || canonicalBlock == null || canonicalBlock.isEmpty()
                || start < 0 || start + canonicalBlock.size() > lines.size()) {
            return false;
        }
        for (int offset = 0; offset < canonicalBlock.size(); offset++) {
            if (!canonicalLoreLine(lines.get(start + offset)).equals(canonicalBlock.get(offset))) {
                return false;
            }
        }
        return true;
    }

    private static void removeSeparatorBefore(List<String> lines, int start) {
        int separatorIndex = start - 1;
        if (separatorIndex >= 0 && MiniMessages.plainText(lines.get(separatorIndex)).isBlank()) {
            lines.remove(separatorIndex);
        }
    }

    static List<String> canonicalLoreLines(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> canonical = new ArrayList<>(lore.size());
        for (String line : lore) {
            canonical.add(canonicalLoreLine(line));
        }
        return List.copyOf(canonical);
    }

    private static String canonicalLoreLine(String line) {
        return MiniMessages.serialize(MiniMessages.parse(Texts.toStringSafe(line)));
    }

    static List<String> staticLoreBlock(List<String> baseLore, List<String> setLore) {
        List<String> canonicalSetLore = canonicalLoreLines(setLore);
        if (canonicalSetLore.isEmpty()) {
            return List.of();
        }
        List<String> block = new ArrayList<>();
        if (baseLore != null && !baseLore.isEmpty()) {
            block.add("");
        }
        block.addAll(canonicalSetLore);
        return List.copyOf(block);
    }

    ItemStack renderSetItem(ItemStack itemStack,
                            EmakiItemDefinition definition,
                            ItemSetMembership membership,
                            EquippedSetState state) {
        if (itemStack == null || definition == null || state == null || state.definition() == null) {
            return itemStack;
        }
        CompiledSetState compiledState = compileState(state);
        SetPresentationTarget target = buildPresentationTarget(definition, compiledState);
        EmakiItemIdentifier.Snapshot identity = identifier.snapshot(itemStack);
        ItemOperationLedger.ReadResult ledgerRead = itemOperationLedger.read(itemStack);
        LedgerFacts ledgerFacts = LedgerFacts.from(ledgerRead);
        SetPresentationInspection inspection = inspectSetPresentation(
                itemStack, ledgerRead, identity, ledgerFacts, definition, membership, state, target);
        return renderSetItem(
                itemStack, definition, membership, state, target, inspection, ledgerRead, ledgerFacts, identity).itemStack();
    }

    private SetItemMutation renderSetItem(ItemStack itemStack,
                                          EmakiItemDefinition definition,
                                          ItemSetMembership membership,
                                          EquippedSetState state,
                                          SetPresentationTarget target,
                                          SetPresentationInspection inspection,
                                          ItemOperationLedger.ReadResult ledgerRead,
                                          LedgerFacts ledgerFacts,
                                          EmakiItemIdentifier.Snapshot identity) {
        if (inspection.current()) {
            return SetItemMutation.success(itemStack, ledgerRead);
        }
        ItemStack original = itemStack.clone();
        if (ledgerRead == null || ledgerRead.corrupt()) {
            return SetItemMutation.failure(original, ledgerRead);
        }
        String setId = membership.setId();
        String previousSetId = identity.setId();
        Integer legacyLoreLines = identity.setLoreLines();
        ItemOperationLedger.ReadResult currentReadResult = ledgerRead;
        boolean previousStaticPresent = Texts.isNotBlank(previousSetId)
                && operation(currentReadResult.entries(), staticLoreOperationId(previousSetId)) != null;
        if (legacyLoreLines != null && legacyLoreLines > 0
                && Texts.isNotBlank(previousSetId)
                && !previousSetId.equals(setId)
                && !previousStaticPresent) {
            return SetItemMutation.failure(original, currentReadResult);
        }

        boolean currentStaticPresent = operation(currentReadResult.entries(), staticLoreOperationId(setId)) != null;
        ItemOperationLedger.UpdateResult reverted = revertNamespaceOperations(
                itemStack, currentReadResult, SET_DISPLAY_NAMESPACE);
        if (!reverted.success() || hasNamespaceOperation(reverted.entries(), SET_DISPLAY_NAMESPACE)) {
            return SetItemMutation.failure(original, currentReadResult);
        }
        currentReadResult = reverted.readResult();
        if (Texts.isNotBlank(previousSetId) && !previousSetId.equals(setId)) {
            if (legacyLoreLines != null && !clearLegacyLoreMarker(itemStack)) {
                return SetItemMutation.failure(original, ledgerRead);
            }
            legacyLoreLines = null;
        }
        if (legacyLoreLines != null && !currentStaticPresent
                && !migrateLegacyStaticLore(itemStack, target.setLore(), legacyLoreLines)) {
            return SetItemMutation.failure(original, ledgerRead);
        }

        List<String> staticBlock = staticLoreBlock(loreLines(itemStack), target.setLore());
        if (!staticBlock.isEmpty()) {
            ItemOperationLedger.UpdateResult staticApply = itemOperationLedger.apply(
                    itemStack,
                    currentReadResult,
                    staticLoreOperationId(setId),
                    SET_DISPLAY_NAMESPACE,
                    List.of(),
                    List.of(Map.of("action", "append", "content", staticBlock)),
                    Map.of()
            );
            if (!staticApply.success()) {
                return SetItemMutation.failure(original, currentReadResult);
            }
            currentReadResult = staticApply.readResult();
        }
        if (target.expectsThresholdOperation()) {
            ItemOperationLedger.UpdateResult thresholdApply = itemOperationLedger.apply(
                    itemStack,
                    currentReadResult,
                    thresholdOperationId(setId),
                    SET_DISPLAY_NAMESPACE,
                    target.nameActions(),
                    target.loreActions(),
                    target.actionVariables()
            );
            if (!thresholdApply.success()) {
                return SetItemMutation.failure(original, currentReadResult);
            }
            currentReadResult = thresholdApply.readResult();
        }
        pdcWriter.writeDynamicSet(
                itemStack,
                definition,
                membership.setId(),
                membership.effectivePieceId(definition.id()),
                state.activeCount(),
                state.definition().totalPieces(),
                target.activeThresholdNumbers(),
                0,
                target.attributes(),
                target.skills(),
                target.signature()
        );
        return SetItemMutation.success(itemStack, currentReadResult);
    }

    private SetPresentationTarget buildPresentationTarget(EmakiItemDefinition definition, CompiledSetState compiledState) {
        List<String> setLore = compiledState.setLore();
        List<Integer> activeThresholdNumbers = compiledState.activeThresholdNumbers();
        Object nameActions = compiledState.nameActions();
        Object loreActions = compiledState.loreActions();
        boolean expectsThresholdOperation = hasActions(nameActions) || hasActions(loreActions);
        Map<String, Object> actionVariables = setActionVariables(
                definition, definition.setMembership(), compiledState.state());
        String signature = SignatureUtil.stableSignature(List.of(
                definition.definitionSignature(),
                compiledState.stateSignature(),
                actionVariables
        ));
        return new SetPresentationTarget(
                List.copyOf(setLore),
                activeThresholdNumbers,
                nameActions,
                loreActions,
                actionVariables,
                compiledState.attributes(),
                compiledState.skills(),
                signature,
                expectsThresholdOperation
        );
    }

    private SetPresentationInspection inspectSetPresentation(ItemStack itemStack,
                                                             ItemOperationLedger.ReadResult ledgerRead,
                                                             EmakiItemIdentifier.Snapshot identity,
                                                             LedgerFacts ledgerFacts,
                                                             EmakiItemDefinition definition,
                                                             ItemSetMembership membership,
                                                             EquippedSetState state,
                                                             SetPresentationTarget target) {
        String setId = membership.setId();
        boolean staticOperationPresent = ledgerFacts.hasOperation(staticLoreOperationId(setId));
        boolean thresholdOperationPresent = ledgerFacts.hasOperation(thresholdOperationId(setId));
        ExpectedPresentationEntries expected = expectedPresentationEntries(itemStack, ledgerRead, setId, target);
        int expectedOperationCount = (expected.staticEntry() == null ? 0 : 1)
                + (expected.thresholdEntry() == null ? 0 : 1);
        boolean operationsCurrent = expected.success()
                && ledgerFacts.setDisplayOperationCount() == expectedOperationCount
                && sameOperationContent(
                        ledgerFacts.operation(staticLoreOperationId(setId)), expected.staticEntry())
                && sameOperationContent(
                        ledgerFacts.operation(thresholdOperationId(setId)), expected.thresholdEntry());
        boolean dynamicStateCurrent = pdcWriter.isDynamicSetCurrent(
                itemStack,
                definition,
                identity,
                setId,
                membership.effectivePieceId(definition.id()),
                state.activeCount(),
                state.definition().totalPieces(),
                target.activeThresholdNumbers(),
                target.attributes(),
                target.skills(),
                target.signature()
        );
        boolean current = !ledgerFacts.corrupt()
                && dynamicStateCurrent
                && identity.setLoreLines() == null
                && staticOperationPresent == (expected.staticEntry() != null)
                && thresholdOperationPresent == (expected.thresholdEntry() != null)
                && operationsCurrent;
        return new SetPresentationInspection(staticOperationPresent, thresholdOperationPresent, current);
    }

    private ExpectedPresentationEntries expectedPresentationEntries(ItemStack itemStack,
                                                                     ItemOperationLedger.ReadResult ledgerRead,
                                                                     String setId,
                                                                     SetPresentationTarget target) {
        if (itemStack == null || ledgerRead == null || ledgerRead.corrupt()) {
            return ExpectedPresentationEntries.failure();
        }
        ItemStack projection = itemStack.clone();
        ItemOperationLedger.UpdateResult reverted = revertNamespaceOperations(
                projection, ledgerRead, SET_DISPLAY_NAMESPACE);
        if (!reverted.success() || hasNamespaceOperation(reverted.entries(), SET_DISPLAY_NAMESPACE)) {
            return ExpectedPresentationEntries.failure();
        }
        ItemOperationLedger.ReadResult currentReadResult = reverted.readResult();

        ItemOperationEntry staticEntry = null;
        List<String> staticBlock = staticLoreBlock(loreLines(projection), target.setLore());
        if (!staticBlock.isEmpty()) {
            ItemOperationLedger.UpdateResult staticApply = itemOperationLedger.apply(
                    projection,
                    currentReadResult,
                    staticLoreOperationId(setId),
                    SET_DISPLAY_NAMESPACE,
                    List.of(),
                    List.of(Map.of("action", "append", "content", staticBlock)),
                    Map.of()
            );
            if (!staticApply.success()) {
                return ExpectedPresentationEntries.failure();
            }
            currentReadResult = staticApply.readResult();
            staticEntry = operation(currentReadResult.entries(), staticLoreOperationId(setId));
        }

        ItemOperationEntry thresholdEntry = null;
        if (target.expectsThresholdOperation()) {
            ItemOperationLedger.UpdateResult thresholdApply = itemOperationLedger.apply(
                    projection,
                    currentReadResult,
                    thresholdOperationId(setId),
                    SET_DISPLAY_NAMESPACE,
                    target.nameActions(),
                    target.loreActions(),
                    target.actionVariables()
            );
            if (!thresholdApply.success()) {
                return ExpectedPresentationEntries.failure();
            }
            currentReadResult = thresholdApply.readResult();
            thresholdEntry = operation(currentReadResult.entries(), thresholdOperationId(setId));
        }
        return new ExpectedPresentationEntries(true, staticEntry, thresholdEntry);
    }

    private ItemOperationLedger.UpdateResult revertNamespaceOperations(ItemStack itemStack,
                                                                       ItemOperationLedger.ReadResult initialReadResult,
                                                                       String sourceNamespace) {
        ItemOperationLedger.ReadResult currentReadResult = initialReadResult == null
                ? ItemOperationLedger.ReadResult.corrupt(List.of())
                : initialReadResult;
        if (currentReadResult.corrupt()) {
            return ItemOperationLedger.UpdateResult.failure(currentReadResult);
        }
        LinkedHashSet<String> operationIds = new LinkedHashSet<>();
        List<ItemOperationEntry> entries = currentReadResult.entries();
        for (int index = entries.size() - 1; index >= 0; index--) {
            ItemOperationEntry entry = entries.get(index);
            if (entry != null && sourceNamespace.equals(entry.sourceNamespace())) {
                operationIds.add(entry.operationId());
            }
        }
        for (String operationId : operationIds) {
            ItemOperationLedger.UpdateResult reverted = itemOperationLedger.revert(
                    itemStack, currentReadResult, operationId);
            if (!reverted.success()) {
                return ItemOperationLedger.UpdateResult.failure(currentReadResult);
            }
            currentReadResult = reverted.readResult();
        }
        return ItemOperationLedger.UpdateResult.success(currentReadResult);
    }

    private boolean hasNamespaceOperation(List<ItemOperationEntry> entries, String sourceNamespace) {
        return entries != null && entries.stream().anyMatch(entry -> entry != null
                && sourceNamespace.equals(entry.sourceNamespace()));
    }

    private ItemOperationEntry operation(List<ItemOperationEntry> entries, String operationId) {
        if (entries == null || Texts.isBlank(operationId)) {
            return null;
        }
        for (int index = entries.size() - 1; index >= 0; index--) {
            ItemOperationEntry entry = entries.get(index);
            if (entry != null && operationId.equals(entry.operationId())) {
                return entry;
            }
        }
        return null;
    }

    private boolean sameOperationContent(ItemOperationEntry actual, ItemOperationEntry expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        return operationContent(actual).equals(operationContent(expected));
    }

    private Map<String, Object> operationContent(ItemOperationEntry entry) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("id", entry.operationId());
        content.put("namespace", entry.sourceNamespace());
        content.put("name", entry.nameRecords().stream().map(ItemOperationEntry.NameOperationRecord::toMap).toList());
        content.put("lore", entry.loreRecords().stream().map(ItemOperationEntry.LoreOperationRecord::toMap).toList());
        return Map.copyOf(content);
    }

    private Map<String, Object> setActionVariables(EmakiItemDefinition definition, ItemSetMembership membership, EquippedSetState state) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("item_id", definition.id());
        variables.put("set_id", membership.setId());
        variables.put("piece_id", membership.effectivePieceId(definition.id()));
        variables.put("set_name", state.definition().displayName());
        variables.put("active", state.activeCount());
        variables.put("active_count", state.activeCount());
        variables.put("total", state.definition().totalPieces());
        variables.put("total_pieces", state.definition().totalPieces());
        variables.put("active_thresholds", state.activeThresholds().stream().map(ItemSetThreshold::requiredPieces).toList());
        return variables;
    }

    static String thresholdOperationId(String setId) {
        return "emakiitem:set_display:" + Texts.normalizeId(setId);
    }

    static String staticLoreOperationId(String setId) {
        return "emakiitem:set_static_lore:" + Texts.normalizeId(setId);
    }

    private boolean hasActions(Object raw) {
        if (raw == null) return false;
        if (raw instanceof Map<?, ?> map) return !map.isEmpty();
        if (raw instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
        return Texts.isNotBlank(raw);
    }

    private boolean operationsFieldPresent(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null && itemMeta.getPersistentDataContainer().getKeys().contains(OPERATIONS_KEY);
    }

    private int loreSize(ItemStack itemStack) {
        return loreLines(itemStack).size();
    }

    private List<String> loreLines(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> lore = itemMeta == null ? null : ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private void debugSetSlot(Player player,
                              String trigger,
                              String actualSlot,
                              EmakiItemDefinition definition,
                              ItemSetMembership membership,
                              ItemSetPieceDefinition pieceDefinition,
                              boolean definitionSlotMatch,
                              boolean setSlotMatch,
                              boolean accepted) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
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

    private void debugSetLore(Player player,
                              String trigger,
                              String slot,
                              String itemId,
                              String previousSignature,
                              Integer previousLoreLines,
                              int previousLoreSize,
                              ItemStack rendered,
                              boolean committed,
                              SetPresentationInspection before,
                              String setId,
                              SetPresentationTarget target) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        boolean expectsOperation = !target.setLore().isEmpty() || target.expectsThresholdOperation();
        SetPresentationInspection after = new SetPresentationInspection(false, expectsOperation, true);
        debugLogger.log("set", player, "set.lore", Map.ofEntries(
                Map.entry("trigger", Texts.toStringSafe(trigger)),
                Map.entry("slot", Texts.toStringSafe(slot)),
                Map.entry("item", Texts.toStringSafe(itemId)),
                Map.entry("signature", Texts.toStringSafe(previousSignature) + "->" + Texts.toStringSafe(identifier.setSignature(rendered))),
                Map.entry("marker", Texts.toStringSafe(previousLoreLines) + "->" + Texts.toStringSafe(identifier.setLoreLines(rendered))),
                Map.entry("lore_size", previousLoreSize + "->" + loreSize(rendered)),
                Map.entry("static_ledger", before.staticOperationPresent() + "->" + after.staticOperationPresent()),
                Map.entry("threshold_ledger", before.thresholdOperationPresent() + "->" + after.thresholdOperationPresent()),
                Map.entry("current", before.current() + "->" + after.current()),
                Map.entry("committed", committed),
                Map.entry("global_owner", threadOwnership != null && threadOwnership.isGlobalOwned()),
                Map.entry("owner", threadOwnership != null && threadOwnership.isEntityOwned(player)),
                Map.entry("thread", Thread.currentThread().getName())
        ));
    }

    private void debugSetWrite(Player player,
                               String trigger,
                               String slot,
                               String itemId,
                               String operation,
                               boolean committed) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.log("set", player, "set.write", Map.of(
                "trigger", Texts.toStringSafe(trigger),
                "slot", Texts.toStringSafe(slot),
                "item", Texts.toStringSafe(itemId),
                "operation", Texts.toStringSafe(operation),
                "committed", committed,
                "owner", threadOwnership != null && threadOwnership.isEntityOwned(player),
                "thread", Thread.currentThread().getName()
        ));
    }

    static ItemSetPieceDefinition resolveSetPiece(ItemSetDefinition setDefinition,
                                                  ItemSetMembership membership,
                                                  String itemId) {
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

    static SetStateSnapshot buildStates(Set<String> visibleSetIds,
                                        Map<String, Set<String>> equippedPiecesBySet,
                                        Function<String, ItemSetDefinition> definitionResolver) {
        Map<String, EquippedSetState> states = new LinkedHashMap<>();
        Set<String> missingDefinitions = new LinkedHashSet<>();
        for (String setId : visibleSetIds == null ? Set.<String>of() : visibleSetIds) {
            if (Texts.isBlank(setId)) {
                continue;
            }
            ItemSetDefinition definition = definitionResolver == null ? null : definitionResolver.apply(setId);
            if (definition == null) {
                missingDefinitions.add(setId);
                continue;
            }
            Set<String> equippedPieces = equippedPiecesBySet == null
                    ? Set.of()
                    : equippedPiecesBySet.getOrDefault(setId, Set.of());
            states.put(setId, new EquippedSetState(definition, equippedPieces));
        }
        return new SetStateSnapshot(states, missingDefinitions);
    }

    private boolean writeInventoryIfUnchanged(PlayerInventory inventory,
                                              int slot,
                                              ItemStack expected,
                                              ItemStack updated) {
        ItemStack current = inventory.getItem(slot);
        if (!sameItem(current, expected)) {
            return false;
        }
        inventory.setItem(slot, updated);
        return true;
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (first == null || first.getType().isAir()) {
            return second == null || second.getType().isAir();
        }
        return first.equals(second);
    }

    private record ListenerSetCache(
            boolean valid,
            long itemGeneration,
            long setGeneration,
            String contributionSignature,
            Map<String, CompiledSetState> compiledStates,
            Map<String, Set<String>> equippedPiecesBySet) {

        private ListenerSetCache {
            itemGeneration = Math.max(0L, itemGeneration);
            setGeneration = Math.max(0L, setGeneration);
            contributionSignature = Texts.toStringSafe(contributionSignature);
            compiledStates = compiledStates == null || compiledStates.isEmpty() ? Map.of() : Map.copyOf(compiledStates);
            if (equippedPiecesBySet == null || equippedPiecesBySet.isEmpty()) {
                equippedPiecesBySet = Map.of();
            } else {
                LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
                equippedPiecesBySet.forEach((setId, pieces) -> copy.put(setId,
                        pieces == null || pieces.isEmpty() ? Set.of() : Set.copyOf(pieces)));
                equippedPiecesBySet = Map.copyOf(copy);
            }
        }
    }

    private static final class CaptureAccumulator {

        private final Map<Integer, SlotFacts> facts = new LinkedHashMap<>();
        private int scannedSlots;
        private int ledgerDecodes;

        private Map<Integer, SlotFacts> facts() {
            return facts;
        }

        private int scannedSlots() {
            return scannedSlots;
        }

        private int ledgerDecodes() {
            return ledgerDecodes;
        }

        private boolean hasCorruptLedger() {
            return facts.values().stream().anyMatch(slotFacts -> slotFacts != null
                    && slotFacts.ledgerRead().status() == ItemOperationLedger.ReadStatus.CORRUPT);
        }

        private void put(SlotFacts slotFacts) {
            facts.put(slotFacts.slot(), slotFacts);
        }

        private void incrementScannedSlots() {
            scannedSlots++;
        }

        private void incrementLedgerDecodes() {
            ledgerDecodes++;
        }
    }

    private record SlotFacts(
            int slot,
            String contributionRole,
            ItemStack expected,
            EmakiItemIdentifier.Snapshot identity,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            ItemOperationLedger.ReadResult ledgerRead,
            LedgerFacts ledgerFacts,
            String managedPresentationDigest) {

        private SlotFacts {
            contributionRole = Texts.toStringSafe(contributionRole);
            identity = identity == null ? EmakiItemIdentifier.Snapshot.empty() : identity;
            membership = membership == null ? ItemSetMembership.empty() : membership;
            ledgerRead = ledgerRead == null ? ItemOperationLedger.ReadResult.corrupt(List.of()) : ledgerRead;
            ledgerFacts = ledgerFacts == null ? LedgerFacts.empty() : ledgerFacts;
            managedPresentationDigest = Texts.toStringSafe(managedPresentationDigest);
        }

        private boolean hasSetPresentation() {
            return Texts.isNotBlank(identity.setId())
                    || Texts.isNotBlank(identity.setSignature())
                    || identity.setLoreLines() != null
                    || ledgerFacts.hasSetDisplayOperations();
        }
    }

    private record LedgerFacts(
            Set<String> operationIds,
            Map<String, ItemOperationEntry> operationsById,
            boolean hasSetDisplayOperations,
            int setDisplayOperationCount,
            boolean corrupt,
            String operationIdentity) {

        private LedgerFacts {
            operationIds = operationIds == null || operationIds.isEmpty() ? Set.of() : Set.copyOf(operationIds);
            operationsById = operationsById == null || operationsById.isEmpty()
                    ? Map.of() : Map.copyOf(operationsById);
            setDisplayOperationCount = Math.max(0, setDisplayOperationCount);
            operationIdentity = Texts.toStringSafe(operationIdentity);
        }

        private static LedgerFacts from(ItemOperationLedger.ReadResult readResult) {
            if (readResult == null) {
                return new LedgerFacts(Set.of(), Map.of(), false, 0, true,
                        SignatureUtil.stableSignature(List.of("CORRUPT", List.of())));
            }
            List<ItemOperationEntry> entries = readResult.entries();
            LinkedHashSet<String> operationIds = new LinkedHashSet<>();
            LinkedHashMap<String, ItemOperationEntry> operationsById = new LinkedHashMap<>();
            List<Map<String, Object>> identity = new ArrayList<>();
            int setDisplayOperationCount = 0;
            for (ItemOperationEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                operationIds.add(entry.operationId());
                operationsById.put(entry.operationId(), entry);
                if (SET_DISPLAY_NAMESPACE.equals(entry.sourceNamespace())) {
                    setDisplayOperationCount++;
                }
                identity.add(entry.toMap());
            }
            boolean corrupt = readResult.status() == ItemOperationLedger.ReadStatus.CORRUPT;
            String operationIdentity = SignatureUtil.stableSignature(List.of(
                    readResult.status().name(),
                    identity
            ));
            return new LedgerFacts(
                    operationIds,
                    operationsById,
                    setDisplayOperationCount > 0,
                    setDisplayOperationCount,
                    corrupt,
                    operationIdentity
            );
        }

        private static LedgerFacts empty() {
            return from(ItemOperationLedger.ReadResult.absent());
        }

        private boolean hasOperation(String operationId) {
            return Texts.isNotBlank(operationId) && operationIds.contains(operationId);
        }

        private ItemOperationEntry operation(String operationId) {
            return Texts.isBlank(operationId) ? null : operationsById.get(operationId);
        }
    }

    private record CompiledSetState(
            EquippedSetState state,
            List<String> setLore,
            List<Integer> activeThresholdNumbers,
            Object nameActions,
            Object loreActions,
            Map<String, Double> attributes,
            List<String> skills,
            String stateSignature) {

        private CompiledSetState {
            setLore = setLore == null || setLore.isEmpty() ? List.of() : List.copyOf(setLore);
            activeThresholdNumbers = activeThresholdNumbers == null || activeThresholdNumbers.isEmpty()
                    ? List.of() : List.copyOf(activeThresholdNumbers);
            nameActions = nameActions == null ? List.of() : nameActions;
            loreActions = loreActions == null ? List.of() : loreActions;
            attributes = attributes == null || attributes.isEmpty() ? Map.of() : Map.copyOf(attributes);
            skills = skills == null || skills.isEmpty() ? List.of() : List.copyOf(skills);
            stateSignature = Texts.toStringSafe(stateSignature);
        }
    }

    private record CompileResult(
            Map<String, CompiledSetState> compiledStates,
            Set<String> missingDefinitions,
            Set<String> missingSetIds,
            int compiles) {

        private CompileResult {
            compiledStates = compiledStates == null || compiledStates.isEmpty() ? Map.of() : Map.copyOf(compiledStates);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of() : Set.copyOf(missingDefinitions);
            missingSetIds = missingSetIds == null || missingSetIds.isEmpty() ? Set.of() : Set.copyOf(missingSetIds);
            compiles = Math.max(0, compiles);
        }
    }

    private record SlotPlan(
            int slot,
            ItemSetRefreshPlanner.SlotAction action,
            SlotFacts facts,
            CompiledSetState compiledState) {
    }

    private record ApplyResult(
            int changed,
            int conflicts,
            boolean contributionChanged,
            Set<String> missingDefinitions,
            Set<String> missingSetIds) {

        private ApplyResult {
            changed = Math.max(0, changed);
            conflicts = Math.max(0, conflicts);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of() : Set.copyOf(missingDefinitions);
            missingSetIds = missingSetIds == null || missingSetIds.isEmpty() ? Set.of() : Set.copyOf(missingSetIds);
        }
    }

    record SetStateSnapshot(Map<String, EquippedSetState> states, Set<String> missingDefinitions) {

        SetStateSnapshot {
            states = states == null || states.isEmpty() ? Map.of() : Map.copyOf(states);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of()
                    : Set.copyOf(missingDefinitions);
        }
    }

    private record SetPresentationTarget(
            List<String> setLore,
            List<Integer> activeThresholdNumbers,
            Object nameActions,
            Object loreActions,
            Map<String, Object> actionVariables,
            Map<String, Double> attributes,
            List<String> skills,
            String signature,
            boolean expectsThresholdOperation) {

        private SetPresentationTarget {
            setLore = setLore == null || setLore.isEmpty() ? List.of() : List.copyOf(setLore);
            activeThresholdNumbers = activeThresholdNumbers == null || activeThresholdNumbers.isEmpty()
                    ? List.of() : List.copyOf(activeThresholdNumbers);
            actionVariables = actionVariables == null || actionVariables.isEmpty()
                    ? Map.of() : Map.copyOf(actionVariables);
            attributes = attributes == null || attributes.isEmpty() ? Map.of() : Map.copyOf(attributes);
            skills = skills == null || skills.isEmpty() ? List.of() : List.copyOf(skills);
            signature = Texts.toStringSafe(signature);
        }
    }

    private record SetPresentationInspection(
            boolean staticOperationPresent,
            boolean thresholdOperationPresent,
            boolean current) {
    }

    private record ExpectedPresentationEntries(
            boolean success,
            ItemOperationEntry staticEntry,
            ItemOperationEntry thresholdEntry) {

        private static ExpectedPresentationEntries failure() {
            return new ExpectedPresentationEntries(false, null, null);
        }
    }

    private record SetItemMutation(
            boolean success,
            ItemStack itemStack,
            ItemOperationLedger.ReadResult readResult) {

        private SetItemMutation {
            readResult = readResult == null
                    ? ItemOperationLedger.ReadResult.corrupt(List.of())
                    : readResult;
        }

        private static SetItemMutation success(ItemStack itemStack, ItemOperationLedger.ReadResult readResult) {
            return new SetItemMutation(true, itemStack, readResult);
        }

        private static SetItemMutation failure(ItemStack itemStack, ItemOperationLedger.ReadResult readResult) {
            return new SetItemMutation(false, itemStack, readResult);
        }

        private List<ItemOperationEntry> entries() {
            return readResult.entries();
        }
    }

}
