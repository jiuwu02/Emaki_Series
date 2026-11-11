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

final class ItemSetListenerScopeRefresher {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemSetLoader setLoader;
    private final EmakiItemIdentifier identifier;
    final ItemOperationLedger itemOperationLedger;
    private final ItemSetPresentationCalculator calculator;
    final Supplier<AppConfig> configSupplier;
    final Supplier<DebugLogger> debugLoggerSupplier;
    private final Logger logger;
    final EmakiScheduling scheduling;
    private final Set<String> warnedMissingSetDefinitions = ConcurrentHashMap.newKeySet();
    private final ItemSetRefreshPlanner refreshPlanner = new ItemSetRefreshPlanner();

    final Map<UUID, Map<String, Integer>> lastActiveCounts = new ConcurrentHashMap<>();
    final Map<UUID, ListenerSetCache> listenerCaches = new ConcurrentHashMap<>();

    ItemSetListenerScopeRefresher(EmakiItemLoader itemLoader,
                                  EmakiItemSetLoader setLoader,
                                  EmakiItemIdentifier identifier,
                                  ItemOperationLedger itemOperationLedger,
                                  ItemSetPresentationCalculator calculator,
                                 Supplier<AppConfig> configSupplier,
                                 Supplier<DebugLogger> debugLoggerSupplier,
                                 Logger logger,
                                 EmakiScheduling scheduling) {
        this.itemLoader = itemLoader;
        this.setLoader = setLoader;
        this.identifier = identifier;
        this.itemOperationLedger = itemOperationLedger;
        this.calculator = calculator;
        this.configSupplier = configSupplier;
        this.debugLoggerSupplier = debugLoggerSupplier;
        this.logger = logger;
        this.scheduling = scheduling;
    }

    public ItemRefreshBatch createRefreshBatch(Player player) { return new ItemSetListenerScopeRefresherDelegate(this).createRefreshBatch(player); }

    public int refreshEquippedSets(Player player, String trigger) { return new ItemSetListenerScopeRefresherDelegate(this).refreshEquippedSets(player, trigger); }

    public int refreshListenerScope(Player player,
                                    String trigger,
                                    Set<Integer> dirtySlots,
                                    boolean forceFull,
                                    boolean contributionDirty) { return new ItemSetListenerScopeRefresherDelegate(this).refreshListenerScope(player, trigger, dirtySlots, forceFull, contributionDirty); }

    public ItemRefreshResult refreshListenerScopeDetailed(Player player,
                                                           Iterable<String> triggers,
                                                           Set<Integer> dirtySlots,
                                                           boolean forceFull,
                                                           boolean contributionDirty,
                                                           Set<RefreshFullReason> requestedFullReasons) { return new ItemSetListenerScopeRefresherDelegate(this).refreshListenerScopeDetailed(player, triggers, dirtySlots, forceFull, contributionDirty, requestedFullReasons); }

    public ItemRefreshResult refreshListenerScopeDetailed(Player player,
                                                           Iterable<String> triggers,
                                                           Set<Integer> dirtySlots,
                                                           boolean forceFull,
                                                           boolean contributionDirty,
                                                           Set<RefreshFullReason> requestedFullReasons,
                                                           ItemRefreshBatch sharedBatch) {
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
        ItemRefreshBatch refreshBatch = sharedBatch != null && sharedBatch.matches(inventory)
                ? sharedBatch
                : new ItemRefreshBatch(inventory, itemOperationLedger);
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
        captureSlots(inventory, heldSlot, initialSlots, itemDefinitions, capture, refreshBatch, false);
        if (capture.hasCorruptLedger()) {
            decision = forceFullForCorruptLedger(decision);
            captureSlots(inventory, heldSlot, allSlots(inventorySize), itemDefinitions, capture, refreshBatch, false);
        }
        String contributionSignature = contributionSignature(capture.facts(), contributionSlots);
        if (decision.scope() == RefreshScope.LOCAL) {
            ListenerSetCache localCache = Objects.requireNonNull(cached, "local refresh cache");
            decision = refreshPlanner.decideContribution(
                    decision,
                    localCache.contributionSignature(),
                    contributionSignature
            );
            if (decision.scope() == RefreshScope.FULL) {
                captureSlots(inventory, heldSlot, allSlots(inventorySize), itemDefinitions, capture, refreshBatch, false);
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
            compiledStates.putAll(Objects.requireNonNull(cached, "local refresh cache").compiledStates());
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
        CompileResult compileResult = calculator.compileStates(
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
            captureSlots(inventory, heldSlot, contributionSlots, itemDefinitions, capture, refreshBatch, true);
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
                CompileResult rebuilt = calculator.compileStates(
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

    private String effectiveSetTrigger(Player player, Iterable<String> triggers) { return new ItemSetListenerScopeRefresherDelegate(this).effectiveSetTrigger(player, triggers); }

    private Set<Integer> contributionSlots(int heldSlot, int inventorySize) { return new ItemSetListenerScopeRefresherDelegate(this).contributionSlots(heldSlot, inventorySize); }

    private Set<Integer> allSlots(int inventorySize) { return new ItemSetListenerScopeRefresherDelegate(this).allSlots(inventorySize); }

    private Set<Integer> unionSlots(Set<Integer> first, Set<Integer> second) { return new ItemSetListenerScopeRefresherDelegate(this).unionSlots(first, second); }

    private Set<String> unionStrings(Set<String> first, Set<String> second) { return new ItemSetListenerScopeRefresherDelegate(this).unionStrings(first, second); }

    private Set<Integer> validSlots(Set<Integer> slots, int inventorySize) { return new ItemSetListenerScopeRefresherDelegate(this).validSlots(slots, inventorySize); }

    private ItemSetRefreshPlanner.Decision forceFullForCorruptLedger(ItemSetRefreshPlanner.Decision decision) { return new ItemSetListenerScopeRefresherDelegate(this).forceFullForCorruptLedger(decision); }

    private void captureSlots(PlayerInventory inventory,
                               int heldSlot,
                               Set<Integer> slots,
                               EmakiItemLoader.Snapshot itemDefinitions,
                               CaptureAccumulator accumulator,
                               ItemRefreshBatch refreshBatch,
                               boolean replace) {
        if (inventory == null || slots == null || accumulator == null || refreshBatch == null) {
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
            int ledgerDecodesBefore = refreshBatch.ledgerDecodes();
            ItemRefreshBatch.SlotSnapshot sharedSnapshot = replace
                    ? refreshBatch.recapture(slot)
                    : refreshBatch.capture(slot);
            ItemStack expected = sharedSnapshot == null ? null : sharedSnapshot.expected();
            ItemMeta itemMeta = expected == null ? null : expected.getItemMeta();
            EmakiItemIdentifier.Snapshot identity = identifier.snapshot(itemMeta);
            EmakiItemDefinition definition = Texts.isBlank(identity.id()) ? null : itemDefinitions.get(identity.id());
            ItemSetMembership membership = definition == null ? ItemSetMembership.empty() : definition.setMembership();
            ItemOperationLedger.ReadResult ledgerRead = sharedSnapshot == null
                    ? ItemOperationLedger.ReadResult.absent()
                    : sharedSnapshot.ledgerRead();
            accumulator.addLedgerDecodes(refreshBatch.ledgerDecodes() - ledgerDecodesBefore);
            LedgerFacts ledgerFacts = LedgerFacts.from(ledgerRead);
            String contributionRole = contributionRole(slot, heldSlot);
            String presentationDigest = Texts.isBlank(contributionRole)
                    ? ""
                    : managedPresentationDigest(itemMeta, identity, ledgerFacts);
            accumulator.put(new SlotFacts(
                    slot,
                    contributionRole,
                    expected,
                    identity,
                    definition,
                    membership,
                    ledgerRead,
                    ledgerFacts,
                    presentationDigest
            ));
            accumulator.incrementScannedSlots();
        }
    }

    String contributionRole(int slot, int heldSlot) { return new ItemSetListenerScopeRefresherDelegate(this).contributionRole(slot, heldSlot); }

    String managedPresentationDigest(ItemMeta itemMeta,
                                             EmakiItemIdentifier.Snapshot identity,
                                             LedgerFacts ledgerFacts) { return new ItemSetListenerScopeRefresherDelegate(this).managedPresentationDigest(itemMeta, identity, ledgerFacts); }

    String contributionSignature(Map<Integer, SlotFacts> factsBySlot, Set<Integer> contributionSlots) { return new ItemSetListenerScopeRefresherDelegate(this).contributionSignature(factsBySlot, contributionSlots); }

    private Map<String, Set<String>> collectEquippedPieces(Player player,
                                                           String trigger,
                                                           Set<Integer> contributionSlots,
                                                           Map<Integer, SlotFacts> factsBySlot,
                                                           EmakiItemSetLoader.Snapshot setDefinitions) { return new ItemSetListenerScopeRefresherDelegate(this).collectEquippedPieces(player, trigger, contributionSlots, factsBySlot, setDefinitions); }

    LinkedHashSet<String> collectVisibleSetIds(Iterable<SlotFacts> facts) { return new ItemSetListenerScopeRefresherDelegate(this).collectVisibleSetIds(facts); }

    List<SlotPlan> planSlots(Set<Integer> targetSlots,
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

    ApplyResult applyPlans(Player player,
                                   PlayerInventory inventory,
                                   String trigger,
                                   List<SlotPlan> plans,
                                   Set<Integer> contributionSlots) {
        int changed = 0;
        int conflicts = 0;
        boolean contributionChanged = false;
        LinkedHashSet<String> missingDefinitions = new LinkedHashSet<>();
        LinkedHashSet<String> missingSetIds = new LinkedHashSet<>();
        ActionContext context = player == null ? null : ActionContext.create(player, "item.set", true);
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
                target = calculator.buildPresentationTarget(facts.definition(), compiledState);
                before = calculator.inspectSetPresentation(
                        facts.expected(),
                        facts.ledgerRead(),
                        facts.identity(),
                        facts.ledgerFacts(),
                        facts.definition(),
                        facts.membership(),
                        compiledState.state(),
                        target,
                        context
                );
                if (before.current()) {
                    continue;
                }
                mutation = calculator.renderSetItem(
                        expected.clone(),
                        facts.definition(),
                        facts.membership(),
                        compiledState.state(),
                        target,
                        before,
                        facts.ledgerRead(),
                        facts.ledgerFacts(),
                        facts.identity(),
                        context
                );
                operation = "apply";
            } else {
                mutation = calculator.clearSetPresentation(
                        expected.clone(), facts.definition(), facts.ledgerRead(), facts.identity());
                operation = "clear";
            }
            ItemStack updated = mutation.itemStack();
            if (!mutation.success() || ItemSetListenerScopeRefresherSupport.sameItem(expected, updated)) {
                continue;
            }
            boolean committed = writeInventoryIfUnchanged(inventory, plan.slot(), expected, updated);
            if (!committed) {
                conflicts++;
                if (plan.action() == ItemSetRefreshPlanner.SlotAction.APPLY) {
                    debugSetLore(player, trigger, "slot_" + plan.slot(), facts.definition().id(),
                            facts.identity().setSignature(), facts.identity().setLoreLines(), calculator.loreSize(expected), updated,
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
                        facts.identity().setSignature(), facts.identity().setLoreLines(), calculator.loreSize(expected), updated,
                        true, before, facts.membership().setId(), target);
            } else {
                debugSetWrite(player, trigger, "slot_" + plan.slot(), facts.identity().id(), operation, true);
            }
        }
        missingDefinitions.remove("");
        missingSetIds.remove("");
        return new ApplyResult(changed, conflicts, contributionChanged, missingDefinitions, missingSetIds);
    }

    String missingDefinitionKey(SlotFacts facts) { return new ItemSetListenerScopeRefresherDelegate(this).missingDefinitionKey(facts); }

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
            logger.warning("[set] 套装刷新时检测到缺失的 EmakiItem 定义; 保留现有展示, "
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

    private Map<String, EquippedSetState> equippedStates(Map<String, CompiledSetState> compiledStates) { return new ItemSetListenerScopeRefresherDelegate(this).equippedStates(compiledStates); }

    private void debugRefreshPlan(Player player,
                                  String trigger,
                                  ItemSetRefreshPlanner.Decision decision,
                                  boolean contributionDirty,
                                  Set<Integer> dirtySlots,
                                  CaptureAccumulator capture,
                                  List<SlotPlan> plans,
                                  ApplyResult applyResult,
                                  int postCaptureScans,
                                  int postCaptureLedgers) { new ItemSetListenerScopeRefresherDelegate(this).debugRefreshPlan(player, trigger, decision, contributionDirty, dirtySlots, capture, plans, applyResult, postCaptureScans, postCaptureLedgers); }

    private void fireSetBonusChangeEvents(Player player,
                                          Map<String, EquippedSetState> states,
                                          Set<String> missingDefinitions,
                                          String trigger) {
        if (player == null || scheduling == null || !scheduling.ownsEntity(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
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

        boolean entityOwned = scheduling != null && scheduling.ownsEntity(player);
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
                Bukkit.getPluginManager().callEvent(new ItemSetBonusChangeEvent(
                        player, setId, oldCount, newCount, totalPieces, activeThresholds, trigger));
            }
        }
        if (current.isEmpty()) {
            lastActiveCounts.remove(uuid);
        } else {
            lastActiveCounts.put(uuid, current);
        }
    }

    public void clearCachedState(UUID uuid) { new ItemSetListenerScopeRefresherDelegate(this).clearCachedState(uuid); }

    public void invalidateCachedState(UUID uuid) { new ItemSetListenerScopeRefresherDelegate(this).invalidateCachedState(uuid); }

    public void clearAllCachedState() {
        lastActiveCounts.clear();
        listenerCaches.clear();
        warnedMissingSetDefinitions.clear();
    }

    void debugSetSlot(Player player,
                              String trigger,
                              String actualSlot,
                              EmakiItemDefinition definition,
                              ItemSetMembership membership,
                              ItemSetPieceDefinition pieceDefinition,
                              boolean definitionSlotMatch,
                              boolean setSlotMatch,
                              boolean accepted) { new ItemSetListenerScopeRefresherDelegate(this).debugSetSlot(player, trigger, actualSlot, definition, membership, pieceDefinition, definitionSlotMatch, setSlotMatch, accepted); }

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
                Map.entry("lore_size", previousLoreSize + "->" + calculator.loreSize(rendered)),
                Map.entry("static_ledger", before.staticOperationPresent() + "->" + after.staticOperationPresent()),
                Map.entry("threshold_ledger", before.thresholdOperationPresent() + "->" + after.thresholdOperationPresent()),
                Map.entry("current", before.current() + "->" + after.current()),
                Map.entry("committed", committed),
                Map.entry("global_owner", scheduling != null && scheduling.ownsGlobal()),
                Map.entry("owner", scheduling != null && scheduling.ownsEntity(player)),
                Map.entry("thread", Thread.currentThread().getName())
        ));
    }

    private void debugSetWrite(Player player,
                               String trigger,
                               String slot,
                               String itemId,
                               String operation,
                               boolean committed) { new ItemSetListenerScopeRefresherDelegate(this).debugSetWrite(player, trigger, slot, itemId, operation, committed); }

    static ItemSetPieceDefinition resolveSetPiece(ItemSetDefinition setDefinition,
                                                  ItemSetMembership membership,
                                                  String itemId) { return ItemSetListenerScopeRefresherDelegate.resolveSetPiece(setDefinition, membership, itemId); }

    private boolean writeInventoryIfUnchanged(PlayerInventory inventory,
                                              int slot,
                                              ItemStack expected,
                                              ItemStack updated) { return new ItemSetListenerScopeRefresherDelegate(this).writeInventoryIfUnchanged(inventory, slot, expected, updated); }


    record ListenerSetCache(
            boolean valid,
            long itemGeneration,
            long setGeneration,
            String contributionSignature,
            Map<String, CompiledSetState> compiledStates,
            Map<String, Set<String>> equippedPiecesBySet) {

        ListenerSetCache {
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

    static final class CaptureAccumulator {

        final Map<Integer, SlotFacts> facts = new LinkedHashMap<>();
        int scannedSlots;
        int ledgerDecodes;

        Map<Integer, SlotFacts> facts() {
            return facts;
        }

        int scannedSlots() {
            return scannedSlots;
        }

        int ledgerDecodes() {
            return ledgerDecodes;
        }

        private boolean hasCorruptLedger() {
            return facts.values().stream().anyMatch(slotFacts -> slotFacts != null
                    && slotFacts.ledgerRead().status() == ItemOperationLedger.ReadStatus.CORRUPT);
        }

        void put(SlotFacts slotFacts) {
            facts.put(slotFacts.slot(), slotFacts);
        }

        private void incrementScannedSlots() {
            scannedSlots++;
        }

        private void addLedgerDecodes(int count) {
            ledgerDecodes += Math.max(0, count);
        }
    }

    record SlotFacts(
            int slot,
            String contributionRole,
            ItemStack expected,
            EmakiItemIdentifier.Snapshot identity,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            ItemOperationLedger.ReadResult ledgerRead,
            LedgerFacts ledgerFacts,
            String managedPresentationDigest) {

        SlotFacts {
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

    record SlotPlan(
            int slot,
            ItemSetRefreshPlanner.SlotAction action,
            SlotFacts facts,
            CompiledSetState compiledState) {
    }

    record ApplyResult(
            int changed,
            int conflicts,
            boolean contributionChanged,
            Set<String> missingDefinitions,
            Set<String> missingSetIds) {

        ApplyResult {
            changed = Math.max(0, changed);
            conflicts = Math.max(0, conflicts);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of() : Set.copyOf(missingDefinitions);
            missingSetIds = missingSetIds == null || missingSetIds.isEmpty() ? Set.of() : Set.copyOf(missingSetIds);
        }
    }

}
