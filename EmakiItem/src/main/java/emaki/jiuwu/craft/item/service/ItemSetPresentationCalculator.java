package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;

/**
 * 套装计算职责：把 {@link EquippedSetState} 编译为可比较的展示目标，并在单个物品上
 * 落地/回滚 set_display 台账与 PDC。不持有玩家维度状态，不做监听域调度。
 */
final class ItemSetPresentationCalculator {

    private static final String SET_DISPLAY_NAMESPACE = "emakiitem:set_display";

    private final EmakiItemIdentifier identifier;
    private final EmakiItemPdcWriter pdcWriter;
    private final ItemSetLoreRenderer loreRenderer;
    private final ItemOperationLedger itemOperationLedger;

    ItemSetPresentationCalculator(EmakiItemIdentifier identifier,
                                  EmakiItemPdcWriter pdcWriter,
                                  ItemSetLoreRenderer loreRenderer,
                                  ItemOperationLedger itemOperationLedger) {
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.loreRenderer = loreRenderer;
        this.itemOperationLedger = itemOperationLedger;
    }

    CompileResult compileStates(Set<String> setIds,
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

    ItemStack clearSetPresentation(ItemStack itemStack, EmakiItemDefinition definition) {
        EmakiItemIdentifier.Snapshot identity = identifier.snapshot(itemStack);
        ItemOperationLedger.ReadResult ledgerRead = itemOperationLedger.read(itemStack);
        return clearSetPresentation(itemStack, definition, ledgerRead, identity).itemStack();
    }

    SetItemMutation clearSetPresentation(ItemStack itemStack,
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

    SetItemMutation renderSetItem(ItemStack itemStack,
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

    SetPresentationTarget buildPresentationTarget(EmakiItemDefinition definition, CompiledSetState compiledState) {
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

    SetPresentationInspection inspectSetPresentation(ItemStack itemStack,
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

    int loreSize(ItemStack itemStack) {
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

    record LedgerFacts(
            Set<String> operationIds,
            Map<String, ItemOperationEntry> operationsById,
            boolean hasSetDisplayOperations,
            int setDisplayOperationCount,
            boolean corrupt,
            String operationIdentity) {

        LedgerFacts {
            operationIds = operationIds == null || operationIds.isEmpty() ? Set.of() : Set.copyOf(operationIds);
            operationsById = operationsById == null || operationsById.isEmpty()
                    ? Map.of() : Map.copyOf(operationsById);
            setDisplayOperationCount = Math.max(0, setDisplayOperationCount);
            operationIdentity = Texts.toStringSafe(operationIdentity);
        }

        static LedgerFacts from(ItemOperationLedger.ReadResult readResult) {
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

        static LedgerFacts empty() {
            return from(ItemOperationLedger.ReadResult.absent());
        }

        private boolean hasOperation(String operationId) {
            return Texts.isNotBlank(operationId) && operationIds.contains(operationId);
        }

        private ItemOperationEntry operation(String operationId) {
            return Texts.isBlank(operationId) ? null : operationsById.get(operationId);
        }
    }

    record CompiledSetState(
            EquippedSetState state,
            List<String> setLore,
            List<Integer> activeThresholdNumbers,
            Object nameActions,
            Object loreActions,
            Map<String, Double> attributes,
            List<String> skills,
            String stateSignature) {

        CompiledSetState {
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

    record CompileResult(
            Map<String, CompiledSetState> compiledStates,
            Set<String> missingDefinitions,
            Set<String> missingSetIds,
            int compiles) {

        CompileResult {
            compiledStates = compiledStates == null || compiledStates.isEmpty() ? Map.of() : Map.copyOf(compiledStates);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of() : Set.copyOf(missingDefinitions);
            missingSetIds = missingSetIds == null || missingSetIds.isEmpty() ? Set.of() : Set.copyOf(missingSetIds);
            compiles = Math.max(0, compiles);
        }
    }

    record SetPresentationTarget(
            List<String> setLore,
            List<Integer> activeThresholdNumbers,
            Object nameActions,
            Object loreActions,
            Map<String, Object> actionVariables,
            Map<String, Double> attributes,
            List<String> skills,
            String signature,
            boolean expectsThresholdOperation) {

        SetPresentationTarget {
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

    record SetPresentationInspection(
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

    record SetItemMutation(
            boolean success,
            ItemStack itemStack,
            ItemOperationLedger.ReadResult readResult) {

        SetItemMutation {
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
