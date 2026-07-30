package emaki.jiuwu.craft.attribute.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.PdcAttributeAccess;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.attribute.model.PdcReadRule.RuleCondition;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;

public final class PdcAttributeService implements PdcAttributeAccess {

    private static final Pattern SOURCE_META_PATTERN = Pattern.compile("%source_meta_([a-zA-Z0-9_\\-.]+)%");
    private static final Pattern SOURCE_ATTRIBUTE_PATTERN = Pattern.compile("%source_(?:attr|attribute)_([a-zA-Z0-9_\\-.]+)%");
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.######"));
    private static final int PATTERN_CACHE_LIMIT = 512;
    private static final ConcurrentHashMap<String, Pattern> COMPILED_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final SnapshotCodec<PdcAttributePayload> PAYLOAD_CODEC = SnapshotCodec.versionedYaml(
            PdcAttributePayload.CURRENT_SCHEMA_VERSION,
            PdcAttributePayload::toMap,
            PdcAttributePayload::fromMap
    );

    private final EmakiAttributePlugin plugin;
    private final PdcReadRuleLoader ruleLoader;
    private final ItemContributionGateRegistry gateRegistry;
    private final PdcService pdcService;
    private final PdcPartition itemPartition;
    private final PdcPartition sourcePartition;
    private final Set<String> registeredSources = ConcurrentHashMap.newKeySet();

    public PdcAttributeService(EmakiAttributePlugin plugin,
            PdcReadRuleLoader ruleLoader,
            ItemContributionGateRegistry gateRegistry) {
        this.plugin = plugin;
        this.ruleLoader = ruleLoader;
        this.gateRegistry = gateRegistry;
        this.pdcService = new PdcService("emaki_attribute", "pdc", plugin == null ? null : plugin.debugLogger());
        this.itemPartition = pdcService.partition("item.attributes");
        this.sourcePartition = itemPartition.child("source");
    }

    @Override
    public EmakiResult<Unit> registerSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return EmakiResult.invalidInput("attribute.pdc.source_id_invalid");
        }
        return registeredSources.add(normalized)
                ? EmakiResult.ok()
                : EmakiResult.rejected("attribute.pdc.source_already_registered");
    }

    @Override
    public EmakiResult<Unit> unregisterSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return EmakiResult.invalidInput("attribute.pdc.source_id_invalid");
        }
        return registeredSources.remove(normalized)
                ? EmakiResult.ok()
                : EmakiResult.notFound("attribute.pdc.source_not_registered");
    }

    @Override
    public boolean isRegisteredSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        return Texts.isNotBlank(normalized) && registeredSources.contains(normalized);
    }

    @Override
    public Set<String> registeredSources() {
        Set<String> copy = new LinkedHashSet<>(registeredSources);
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }

    @Override
    public EmakiResult<Unit> write(ItemStack itemStack, PdcAttributePayload payload) {
        if (itemStack == null || itemStack.getType().isAir() || payload == null || Texts.isBlank(payload.sourceId())) {
            return EmakiResult.invalidInput("attribute.pdc.write_input_invalid");
        }
        if (!isRegisteredSource(payload.sourceId())) {
            plugin.getLogger().warning("Ignoring PDC attribute write for unregistered source: " + payload.sourceId());
            return EmakiResult.rejected("attribute.pdc.source_not_registered");
        }
        try {
            persist(itemStack, payload);
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.pdc.write_failed");
        }
    }

    private void persist(ItemStack itemStack, PdcAttributePayload payload) {
        pdcService.writeBlob(itemStack, sourcePartition.child(payload.sourceId()), "payload", PAYLOAD_CODEC, payload);
        writeIndex(itemStack, addSource(readIndex(itemStack), payload.sourceId()));
    }

    @Override
    public EmakiResult<PdcAttributePayload> read(ItemStack itemStack, String sourceId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceId)) {
            return EmakiResult.invalidInput("attribute.pdc.read_input_invalid");
        }
        try {
            PdcAttributePayload payload = rawRead(itemStack, sourceId);
            return payload == null
                    ? EmakiResult.notFound("attribute.pdc.payload_not_found")
                    : EmakiResult.success(payload);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.pdc.read_failed");
        }
    }

    private PdcAttributePayload rawRead(ItemStack itemStack, String sourceId) {
        if (itemStack == null || Texts.isBlank(sourceId)) {
            return null;
        }
        return pdcService.readBlob(itemStack, sourcePartition.child(Texts.normalizeId(sourceId)), "payload", PAYLOAD_CODEC);
    }

    @Override
    public Map<String, PdcAttributePayload> readAll(ItemStack itemStack) {
        Map<String, PdcAttributePayload> result = new LinkedHashMap<>();
        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        for (String sourceId : readIndex(itemStack)) {
            PdcAttributePayload payload = rawRead(itemStack, sourceId);
            if (payload != null) {
                result.put(sourceId, payload);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    @Override
    public EmakiResult<Unit> copy(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        if (fromItem == null || fromItem.getType().isAir() || toItem == null || toItem.getType().isAir()) {
            return EmakiResult.invalidInput("attribute.pdc.copy_input_invalid");
        }
        try {
            Set<String> excluded = normalizeIds(excludedSourceIds);
            for (PdcAttributePayload payload : readAll(fromItem).values()) {
                if (payload == null || Texts.isBlank(payload.sourceId()) || excluded.contains(payload.sourceId())) {
                    continue;
                }
                // Copy preserves persisted blocks even when their owning source is temporarily absent.
                persist(toItem, payload);
            }
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.pdc.copy_failed");
        }
    }

    private Set<String> normalizeIds(Set<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String sourceId : sourceIds) {
            String value = Texts.normalizeId(sourceId);
            if (Texts.isNotBlank(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    @Override
    public EmakiResult<Unit> clear(ItemStack itemStack, String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(normalized)) {
            return EmakiResult.invalidInput("attribute.pdc.clear_input_invalid");
        }
        if (rawRead(itemStack, normalized) == null) {
            return EmakiResult.notFound("attribute.pdc.payload_not_found");
        }
        try {
            pdcService.remove(itemStack, sourcePartition.child(normalized), "payload");
            writeIndex(itemStack, removeSource(readIndex(itemStack), normalized));
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.pdc.clear_failed");
        }
    }

    @Override
    public EmakiResult<Unit> clearAll(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("attribute.pdc.clear_all_input_invalid");
        }
        try {
            for (String sourceId : readIndex(itemStack)) {
                pdcService.remove(itemStack, sourcePartition.child(sourceId), "payload");
            }
            pdcService.remove(itemStack, itemPartition, "source_index");
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("attribute.pdc.clear_all_failed");
        }
    }

    PdcAttributeCollection collectRawContribution(ItemStack itemStack) {
        return collectRawContribution(readAll(itemStack).values());
    }

    PdcAttributeCollection collectFilteredContribution(Player player, ItemStack itemStack) {
        return collectContributionViews(player, itemStack, null).filtered();
    }

    PdcAttributeViews collectContributionViews(Player player, ItemStack itemStack) {
        return collectContributionViews(player, itemStack, null);
    }

    PdcAttributeViews collectContributionViews(Player player, ItemStack itemStack, String actualSlot) {
        Map<String, PdcAttributePayload> payloads = readAll(itemStack);
        PdcAttributeCollection raw = collectRawContribution(payloads.values());
        ItemSlotGate itemSlotGate = resolveItemSlotGate(payloads.values(), actualSlot);
        String rejectingGate = resolveRejectingGateId(player, itemStack, actualSlot);
        if (player == null || payloads.isEmpty()) {
            return new PdcAttributeViews(
                    raw,
                    raw,
                    itemSlotGate.declaredSlots(),
                    itemSlotGate.matched(),
                    rejectingGate
            );
        }
        return new PdcAttributeViews(
                raw,
                collectFilteredContribution(player, itemStack, payloads, actualSlot, itemSlotGate, rejectingGate),
                itemSlotGate.declaredSlots(),
                itemSlotGate.matched(),
                rejectingGate
        );
    }

    /**
     * Resolves the id of the first registered gate rejecting the item.
     *
     * @param player the owning player
     * @param itemStack the equipped item
     * @param actualSlot the equipment slot being collected
     * @return the rejecting gate id, or {@code ""} when accepted
     */
    String resolveRejectingGateId(Player player, ItemStack itemStack, String actualSlot) {
        if (gateRegistry == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        return gateRegistry.rejectingGateId(player, itemStack, actualSlot);
    }

    private PdcAttributeCollection collectFilteredContribution(Player player,
            ItemStack itemStack,
            Map<String, PdcAttributePayload> payloads,
            String actualSlot,
            ItemSlotGate itemSlotGate,
            String rejectingGateId) {
        List<String> loreLines = readLoreLines(itemStack);
        Map<String, Double> values = new LinkedHashMap<>();
        List<Object> signatureParts = new ArrayList<>();
        signatureParts.add(Map.of(
                "scope", "item_slot_gate",
                "actual_slot", Texts.toStringSafe(actualSlot),
                "declared_slots", itemSlotGate.declaredSlots(),
                "matched", itemSlotGate.matched()
        ));
        boolean gateActive = Texts.isBlank(rejectingGateId);
        signatureParts.add(Map.of(
                "scope", "item_condition_gate",
                "provider_id", Texts.toStringSafe(rejectingGateId),
                "active", gateActive
        ));
        if (!gateActive) {
            return new PdcAttributeCollection(Map.of(), SignatureUtil.stableSignature(signatureParts));
        }
        for (Map.Entry<String, PdcAttributePayload> entry : payloads.entrySet()) {
            String sourceId = entry.getKey();
            PdcAttributePayload payload = entry.getValue();
            PdcReadRule rule = ruleLoader == null ? null : ruleLoader.get(sourceId);
            boolean slotMatched = matchesEquipmentSlot(payload, actualSlot);
            if (rule == null) {
                if (itemSlotGate.matched() && slotMatched) {
                    mergeValues(values, payload.attributes());
                }
                signatureParts.add(Map.of(
                        "source_id", sourceId,
                        "mode", "default",
                        "payload", payload.toMap(),
                        "actual_slot", Texts.toStringSafe(actualSlot),
                        "slot_matched", slotMatched,
                        "item_slot_matched", itemSlotGate.matched()
                ));
                continue;
            }
            FilterOutcome outcome = evaluateRule(player, payload, rule, loreLines);
            if (itemSlotGate.matched() && slotMatched && outcome.accepted()) {
                mergeValues(values, payload.attributes());
            }
            signatureParts.add(Map.of(
                    "source_id", sourceId,
                    "payload", payload.toMap(),
                    "rule", rule.toMap(),
                    "actual_slot", Texts.toStringSafe(actualSlot),
                    "slot_matched", slotMatched,
                    "item_slot_matched", itemSlotGate.matched(),
                    "resolved_conditions", outcome.resolvedConditions(),
                    "accepted", outcome.accepted() && slotMatched && itemSlotGate.matched()
            ));
        }
        return new PdcAttributeCollection(
                values.isEmpty() ? Map.of() : Map.copyOf(values),
                SignatureUtil.stableSignature(signatureParts)
        );
    }

    private PdcAttributeCollection collectRawContribution(Collection<PdcAttributePayload> payloads) {
        Map<String, Double> values = new LinkedHashMap<>();
        List<Object> signatureParts = new ArrayList<>();
        if (payloads != null) {
            for (PdcAttributePayload payload : payloads) {
                if (payload == null || Texts.isBlank(payload.sourceId())) {
                    continue;
                }
                mergeValues(values, payload.attributes());
                signatureParts.add(payload.toMap());
            }
        }
        return new PdcAttributeCollection(
                values.isEmpty() ? Map.of() : Map.copyOf(values),
                SignatureUtil.stableSignature(signatureParts)
        );
    }

    private FilterOutcome evaluateRule(Player player,
            PdcAttributePayload payload,
            PdcReadRule rule,
            List<String> loreLines) {
        return evaluateFlexibleRule(player, payload, rule, loreLines);
    }

    private FilterOutcome evaluateFlexibleRule(Player player,
            PdcAttributePayload payload,
            PdcReadRule rule,
            List<String> loreLines) {
        if (rule == null || !rule.hasConditions()) {
            return new FilterOutcome(true, List.of());
        }
        List<String> traces = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        for (RuleCondition condition : rule.conditions()) {
            ConditionOutcome outcome = evaluateCondition(player, payload, loreLines, condition);
            traces.addAll(outcome.traces());
            if (outcome.accepted() == null) {
                if (rule.invalidAsFailure()) {
                    return new FilterOutcome(false, traces);
                }
                continue;
            }
            results.add(outcome.accepted());
        }
        if (results.isEmpty()) {
            return new FilterOutcome(!rule.invalidAsFailure(), traces);
        }
        return new FilterOutcome(combineResults(results, rule.conditionType(), rule.requiredCount()), traces);
    }

    private ConditionOutcome evaluateCondition(Player player,
            PdcAttributePayload payload,
            List<String> loreLines,
            RuleCondition condition) {
        if (payload == null || condition == null || Texts.isBlank(condition.type())) {
            return ConditionOutcome.invalid("condition=invalid");
        }
        return switch (normalizeConditionType(condition.type())) {
            case "pdc_meta" ->
                evaluateSingleValueCondition(player, payload, condition, payload.meta().get(Texts.normalizeId(condition.key())), "pdc_meta");
            case "pdc_attribute" ->
                evaluateSingleValueCondition(player, payload, condition, formatNumber(payload.attributes().get(Texts.normalizeId(condition.key()))), "pdc_attribute");
            case "source_id" ->
                evaluateSingleValueCondition(player, payload, condition, payload.sourceId(), "source_id");
            case "lore_regex" ->
                evaluateLoreRegexCondition(player, payload, loreLines, condition);
            default ->
                ConditionOutcome.invalid("condition.type=" + condition.type());
        };
    }

    private ConditionOutcome evaluateSingleValueCondition(Player player,
            PdcAttributePayload payload,
            RuleCondition condition,
            String rawValue,
            String typeName) {
        String value = Texts.toStringSafe(rawValue);
        if (condition.hasPattern()) {
            Pattern pattern = compileRulePattern(condition.pattern());
            if (pattern == null) {
                return ConditionOutcome.invalid(typeName + " key=" + condition.key() + " invalid_pattern=" + condition.pattern());
            }
            Matcher matcher = pattern.matcher(value);
            if (!matcher.find()) {
                return ConditionOutcome.of(
                        condition.requireMatch() ? Boolean.FALSE : Boolean.TRUE,
                        typeName + " key=" + condition.key() + " value=" + value + " pattern=" + condition.pattern() + " matched=false"
                );
            }
            if (!condition.hasCondition()) {
                return ConditionOutcome.of(
                        Boolean.TRUE,
                        typeName + " key=" + condition.key() + " value=" + value + " pattern=" + condition.pattern() + " matched=true"
                );
            }
            String resolvedCondition = applyFlexibleContext(player, payload, condition.condition(), value, matcher);
            Boolean accepted = evaluateResolvedCondition(resolvedCondition);
            return new ConditionOutcome(
                    accepted,
                    List.of(typeName + " key=" + condition.key() + " value=" + value + " pattern=" + condition.pattern()
                            + " condition=" + resolvedCondition + " accepted=" + accepted)
            );
        }
        if (!condition.hasCondition()) {
            boolean accepted = Texts.isNotBlank(value);
            return ConditionOutcome.of(
                    accepted,
                    typeName + " key=" + condition.key() + " value=" + value + " exists=" + accepted
            );
        }
        String resolvedCondition = applyFlexibleContext(player, payload, condition.condition(), value, null);
        Boolean accepted = evaluateResolvedCondition(resolvedCondition);
        return new ConditionOutcome(
                accepted,
                List.of(typeName + " key=" + condition.key() + " value=" + value + " condition=" + resolvedCondition + " accepted=" + accepted)
        );
    }

    private ConditionOutcome evaluateLoreRegexCondition(Player player,
            PdcAttributePayload payload,
            List<String> loreLines,
            RuleCondition condition) {
        Pattern pattern = compileRulePattern(condition.pattern());
        if (pattern == null) {
            return ConditionOutcome.invalid("lore_regex invalid_pattern=" + condition.pattern());
        }
        if (loreLines == null || loreLines.isEmpty()) {
            return ConditionOutcome.of(
                    condition.requireMatch() ? Boolean.FALSE : Boolean.TRUE,
                    "lore_regex pattern=" + condition.pattern() + " lore_empty=true"
            );
        }
        boolean matched = false;
        boolean sawInvalid = false;
        List<String> traces = new ArrayList<>();
        for (String line : loreLines) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            matched = true;
            if (!condition.hasCondition()) {
                return ConditionOutcome.of(Boolean.TRUE, "lore_regex pattern=" + condition.pattern() + " line=" + line + " matched=true");
            }
            String resolvedCondition = applyFlexibleContext(player, payload, condition.condition(), line, matcher);
            Boolean accepted = evaluateResolvedCondition(resolvedCondition);
            traces.add("lore_regex pattern=" + condition.pattern() + " line=" + line + " condition=" + resolvedCondition + " accepted=" + accepted);
            if (accepted == null) {
                sawInvalid = true;
                continue;
            }
            if (accepted) {
                return new ConditionOutcome(Boolean.TRUE, traces);
            }
        }
        if (!matched) {
            return ConditionOutcome.of(
                    condition.requireMatch() ? Boolean.FALSE : Boolean.TRUE,
                    "lore_regex pattern=" + condition.pattern() + " matched=false"
            );
        }
        if (sawInvalid) {
            return new ConditionOutcome(null, traces);
        }
        return new ConditionOutcome(Boolean.FALSE, traces);
    }

    private Boolean evaluateResolvedCondition(String resolvedCondition) {
        if (Texts.isBlank(resolvedCondition)) {
            return null;
        }
        return ConditionEvaluator.evaluateSingle(resolvedCondition, text -> text);
    }

    private String applyFlexibleContext(Player player,
            PdcAttributePayload payload,
            String text,
            String value,
            Matcher matcher) {
        String resolved = Texts.toStringSafe(text).replace("%value%", Texts.toStringSafe(value));
        resolved = replaceCaptureGroups(resolved, matcher);
        return resolvePlaceholders(player, payload, resolved);
    }

    private String replaceCaptureGroups(String text, Matcher matcher) {
        if (Texts.isBlank(text) || matcher == null) {
            return text;
        }
        String result = text.replace("$0", Texts.toStringSafe(matcher.group()));
        for (int index = matcher.groupCount(); index >= 1; index--) {
            result = result.replace("$" + index, Texts.toStringSafe(matcher.group(index)));
        }
        return result;
    }

    private Pattern compileRulePattern(String pattern) {
        if (Texts.isBlank(pattern)) {
            return null;
        }
        if (COMPILED_PATTERN_CACHE.size() >= PATTERN_CACHE_LIMIT) {
            COMPILED_PATTERN_CACHE.clear();
        }
        return COMPILED_PATTERN_CACHE.computeIfAbsent(pattern, key -> {
            try {
                return Pattern.compile(key, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (Exception _) {
                return null;
            }
        });
    }

    private boolean combineResults(List<Boolean> results, String conditionType, Integer requiredCount) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        String mode = Texts.lower(conditionType);
        if ("any_of".equals(mode)) {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
        if ("at_least".equals(mode)) {
            int count = requiredCount == null ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() >= count;
        }
        if ("exactly".equals(mode)) {
            int count = requiredCount == null ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() == count;
        }
        if ("none_of".equals(mode)) {
            return results.stream().noneMatch(Boolean::booleanValue);
        }
        return results.stream().allMatch(Boolean::booleanValue);
    }

    private List<String> readLoreLines(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            return List.of();
        }
        var lore = ItemTextBridge.loreLines(itemMeta);
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (var line : lore) {
            String plain = Texts.normalizeWhitespace(MiniMessages.plainText(line));
            if (Texts.isNotBlank(plain)) {
                lines.add(plain);
            }
        }
        return lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    static ItemSlotGate resolveItemSlotGate(Collection<PdcAttributePayload> payloads, String actualSlot) {
        if (payloads == null || payloads.isEmpty()) {
            return new ItemSlotGate(List.of(), true);
        }
        Set<String> declaredSlots = new LinkedHashSet<>();
        boolean matched = true;
        for (PdcAttributePayload payload : payloads) {
            if (payload == null) {
                continue;
            }
            String requiredSlot = payload.meta().get(EquipmentSlotMatcher.ACTIVE_SLOT_META_KEY);
            if (Texts.isBlank(requiredSlot)) {
                continue;
            }
            String normalizedRequired = EquipmentSlotMatcher.normalizeRequired(requiredSlot);
            declaredSlots.add(normalizedRequired);
            if (!EquipmentSlotMatcher.matches(actualSlot, normalizedRequired)) {
                matched = false;
            }
        }
        return new ItemSlotGate(
                declaredSlots.isEmpty() ? List.of() : List.copyOf(declaredSlots),
                matched
        );
    }

    private boolean matchesEquipmentSlot(PdcAttributePayload payload, String actualSlot) {
        if (payload == null) {
            return true;
        }
        String requiredSlot = payload.meta().get(EquipmentSlotMatcher.ACTIVE_SLOT_META_KEY);
        return EquipmentSlotMatcher.matches(actualSlot, requiredSlot);
    }

    private String resolvePlaceholders(Player player, PdcAttributePayload payload, String text) {
        String resolved = replaceTokenPattern(text, SOURCE_META_PATTERN, key -> payload.meta().getOrDefault(Texts.normalizeId(key), ""));
        resolved = replaceTokenPattern(resolved, SOURCE_ATTRIBUTE_PATTERN, key -> formatNumber(payload.attributes().get(Texts.normalizeId(key))));
        resolved = resolved.replace("%source_id%", payload.sourceId());
        return applyPlayerPlaceholders(player, resolved);
    }

    private String applyPlayerPlaceholders(Player player, String text) {
        if (Texts.isBlank(text) || player == null) {
            return text;
        }
        String resolved = text
                .replace("%player_name%", player.getName())
                .replace("%player_level%", Integer.toString(player.getLevel()));
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                resolved = PlaceholderAPI.setPlaceholders(player, resolved);
            } catch (Exception _) {
            }
        }
        return resolved;
    }

    private String replaceTokenPattern(String text, Pattern pattern, java.util.function.Function<String, String> resolver) {
        if (Texts.isBlank(text) || pattern == null || resolver == null) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = resolver.apply(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Texts.toStringSafe(replacement)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "";
        }
        return DECIMAL_FORMAT.get().format(value);
    }

    private void mergeValues(Map<String, Double> target, Map<String, Double> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            target.merge(Texts.normalizeId(entry.getKey()), entry.getValue(), Double::sum);
        }
    }

    private List<String> addSource(List<String> current, String sourceId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(current == null ? List.of() : current);
        ids.add(Texts.normalizeId(sourceId));
        return new ArrayList<>(ids);
    }

    private List<String> removeSource(List<String> current, String sourceId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(current == null ? List.of() : current);
        ids.remove(Texts.normalizeId(sourceId));
        return new ArrayList<>(ids);
    }

    private List<String> readIndex(ItemStack itemStack) {
        if (itemStack == null) {
            return List.of();
        }
        String raw = pdcService.get(itemStack, itemPartition, "source_index", PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String normalized = Texts.normalizeId(entry);
            if (Texts.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private void writeIndex(ItemStack itemStack, List<String> sourceIds) {
        if (itemStack == null) {
            return;
        }
        if (sourceIds == null || sourceIds.isEmpty()) {
            pdcService.remove(itemStack, itemPartition, "source_index");
            return;
        }
        pdcService.set(itemStack, itemPartition, "source_index", PersistentDataType.STRING, String.join(",", sourceIds));
    }
    private String normalizeConditionType(String type) {
        return Texts.normalizeId(type);
    }

    record PdcAttributeCollection(Map<String, Double> values, String sourceSignature) {

        PdcAttributeCollection {
            values = values == null ? Map.of() : Map.copyOf(values);
            sourceSignature = sourceSignature == null ? "" : sourceSignature;
        }
    }

    record PdcAttributeViews(PdcAttributeCollection raw,
            PdcAttributeCollection filtered,
            List<String> declaredSlots,
            boolean itemSlotMatched,
            String rejectingGateId) {

        PdcAttributeViews {
            raw = raw == null ? new PdcAttributeCollection(Map.of(), "") : raw;
            filtered = filtered == null ? raw : filtered;
            declaredSlots = declaredSlots == null ? List.of() : List.copyOf(declaredSlots);
            rejectingGateId = rejectingGateId == null ? "" : rejectingGateId;
        }

        boolean hasExplicitSlotConstraint() {
            return !declaredSlots.isEmpty();
        }

        /** {@return whether every registered item contribution gate accepted the item} */
        boolean itemContributionActive() {
            return rejectingGateId.isEmpty();
        }
    }

    record ItemSlotGate(List<String> declaredSlots, boolean matched) {

        ItemSlotGate {
            declaredSlots = declaredSlots == null ? List.of() : List.copyOf(declaredSlots);
        }
    }

    private record FilterOutcome(boolean accepted, List<String> resolvedConditions) {

        private FilterOutcome {
            resolvedConditions = resolvedConditions == null ? List.of() : List.copyOf(resolvedConditions);
        }
    }

    private record ConditionOutcome(Boolean accepted, List<String> traces) {

        private ConditionOutcome {
            traces = traces == null ? List.of() : List.copyOf(traces);
        }

        static ConditionOutcome of(Boolean accepted, String trace) {
            return new ConditionOutcome(accepted, Texts.isBlank(trace) ? List.of() : List.of(trace));
        }

        static ConditionOutcome invalid(String trace) {
            return of(null, trace);
        }
    }
}
