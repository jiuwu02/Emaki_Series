package emaki.jiuwu.craft.attribute.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.attribute.model.PdcReadRule.RuleCheck;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;

public final class PdcAttributeService implements PdcAttributeApi {

    private static final Pattern SOURCE_META_PATTERN = Pattern.compile("%source_meta_([a-zA-Z0-9_\\-.]+)%");
    private static final Pattern SOURCE_ATTRIBUTE_PATTERN = Pattern.compile("%source_(?:attr|attribute)_([a-zA-Z0-9_\\-.]+)%");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.######");
    private static final SnapshotCodec<PdcAttributePayload> PAYLOAD_CODEC = SnapshotCodec.yaml(
            PdcAttributePayload::toMap,
            PdcAttributePayload::fromMap
    );

    private final EmakiAttributePlugin plugin;
    private final PdcReadRuleLoader ruleLoader;
    private final PdcService pdcService = new PdcService("emaki_attribute");
    private final PdcPartition itemPartition = pdcService.partition("item.attributes");
    private final PdcPartition sourcePartition = itemPartition.child("source");
    private final Set<String> registeredSources = ConcurrentHashMap.newKeySet();

    public PdcAttributeService(EmakiAttributePlugin plugin, PdcReadRuleLoader ruleLoader) {
        this.plugin = plugin;
        this.ruleLoader = ruleLoader;
    }

    @Override
    public boolean registerSource(String sourceId) {
        String normalized = normalizeId(sourceId);
        return Texts.isNotBlank(normalized) && registeredSources.add(normalized);
    }

    @Override
    public void unregisterSource(String sourceId) {
        String normalized = normalizeId(sourceId);
        if (Texts.isNotBlank(normalized)) {
            registeredSources.remove(normalized);
        }
    }

    @Override
    public boolean isRegisteredSource(String sourceId) {
        String normalized = normalizeId(sourceId);
        return Texts.isNotBlank(normalized) && registeredSources.contains(normalized);
    }

    @Override
    public Set<String> registeredSources() {
        Set<String> copy = new LinkedHashSet<>(registeredSources);
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }

    @Override
    public boolean write(ItemStack itemStack, PdcAttributePayload payload) {
        if (itemStack == null || payload == null || Texts.isBlank(payload.sourceId())) {
            return false;
        }
        if (!isRegisteredSource(payload.sourceId())) {
            plugin.getLogger().warning("Ignoring PDC attribute write for unregistered source: " + payload.sourceId());
            return false;
        }
        pdcService.writeBlob(itemStack, sourcePartition.child(payload.sourceId()), "payload", PAYLOAD_CODEC, payload);
        writeIndex(itemStack, addSource(readIndex(itemStack), payload.sourceId()));
        return true;
    }

    @Override
    public PdcAttributePayload read(ItemStack itemStack, String sourceId) {
        if (itemStack == null || Texts.isBlank(sourceId)) {
            return null;
        }
        return pdcService.readBlob(itemStack, sourcePartition.child(normalizeId(sourceId)), "payload", PAYLOAD_CODEC);
    }

    @Override
    public Map<String, PdcAttributePayload> readAll(ItemStack itemStack) {
        Map<String, PdcAttributePayload> result = new LinkedHashMap<>();
        if (itemStack == null) {
            return result;
        }
        for (String sourceId : readIndex(itemStack)) {
            PdcAttributePayload payload = read(itemStack, sourceId);
            if (payload != null) {
                result.put(sourceId, payload);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    @Override
    public boolean clear(ItemStack itemStack, String sourceId) {
        String normalized = normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized)) {
            return false;
        }
        pdcService.remove(itemStack, sourcePartition.child(normalized), "payload");
        writeIndex(itemStack, removeSource(readIndex(itemStack), normalized));
        return true;
    }

    @Override
    public void clearAll(ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }
        for (String sourceId : readIndex(itemStack)) {
            pdcService.remove(itemStack, sourcePartition.child(sourceId), "payload");
        }
        pdcService.remove(itemStack, itemPartition, "source_index");
    }

    PdcAttributeCollection collectRawContribution(ItemStack itemStack) {
        return collectRawContribution(readAll(itemStack).values());
    }

    PdcAttributeCollection collectFilteredContribution(Player player, ItemStack itemStack) {
        Map<String, PdcAttributePayload> payloads = readAll(itemStack);
        if (player == null || payloads.isEmpty()) {
            return collectRawContribution(payloads.values());
        }
        List<String> loreLines = readLoreLines(itemStack);
        Map<String, Double> values = new LinkedHashMap<>();
        List<Object> signatureParts = new ArrayList<>();
        for (Map.Entry<String, PdcAttributePayload> entry : payloads.entrySet()) {
            String sourceId = entry.getKey();
            PdcAttributePayload payload = entry.getValue();
            PdcReadRule rule = ruleLoader == null ? null : ruleLoader.get(sourceId);
            if (rule == null) {
                mergeValues(values, payload.attributes());
                signatureParts.add(Map.of(
                        "source_id", sourceId,
                        "mode", "default",
                        "payload", payload.toMap()
                ));
                continue;
            }
            FilterOutcome outcome = evaluateRule(player, payload, rule, loreLines);
            if (outcome.accepted()) {
                mergeValues(values, payload.attributes());
            }
            signatureParts.add(Map.of(
                    "source_id", sourceId,
                    "payload", payload.toMap(),
                    "rule", rule.toMap(),
                    "resolved_conditions", outcome.resolvedConditions(),
                    "accepted", outcome.accepted()
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
        if (rule != null && rule.usesFlexibleChecks()) {
            return evaluateFlexibleRule(player, payload, rule, loreLines);
        }
        return evaluateLegacyRule(player, payload, rule);
    }

    private FilterOutcome evaluateLegacyRule(Player player, PdcAttributePayload payload, PdcReadRule rule) {
        List<String> resolvedConditions = new ArrayList<>();
        for (String condition : rule.conditions()) {
            resolvedConditions.add(resolvePlaceholders(player, payload, condition));
        }
        boolean accepted = ConditionEvaluator.evaluate(
                rule.conditions(),
                rule.conditionType(),
                rule.requiredCount(),
                text -> resolvePlaceholders(player, payload, text),
                rule.invalidAsFailure()
        );
        return new FilterOutcome(accepted, resolvedConditions);
    }

    private FilterOutcome evaluateFlexibleRule(Player player,
            PdcAttributePayload payload,
            PdcReadRule rule,
            List<String> loreLines) {
        if (rule == null || rule.checks().isEmpty()) {
            return new FilterOutcome(true, List.of());
        }
        List<String> traces = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        for (RuleCheck check : rule.checks()) {
            CheckOutcome outcome = evaluateCheck(player, payload, loreLines, check);
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

    private CheckOutcome evaluateCheck(Player player,
            PdcAttributePayload payload,
            List<String> loreLines,
            RuleCheck check) {
        if (payload == null || check == null || Texts.isBlank(check.type())) {
            return CheckOutcome.invalid("check=invalid");
        }
        return switch (normalizeCheckType(check.type())) {
            case "pdc_meta" ->
                evaluateSingleValueCheck(player, payload, check, payload.meta().get(normalizeId(check.key())), "pdc_meta");
            case "pdc_attribute" ->
                evaluateSingleValueCheck(player, payload, check, formatNumber(payload.attributes().get(normalizeId(check.key()))), "pdc_attribute");
            case "source_id" ->
                evaluateSingleValueCheck(player, payload, check, payload.sourceId(), "source_id");
            case "lore_regex" ->
                evaluateLoreRegexCheck(player, payload, loreLines, check);
            default ->
                CheckOutcome.invalid("check_type=" + check.type());
        };
    }

    private CheckOutcome evaluateSingleValueCheck(Player player,
            PdcAttributePayload payload,
            RuleCheck check,
            String rawValue,
            String typeName) {
        String value = Texts.toStringSafe(rawValue);
        if (check.hasPattern()) {
            Pattern pattern = compileRulePattern(check.pattern());
            if (pattern == null) {
                return CheckOutcome.invalid(typeName + " key=" + check.key() + " invalid_pattern=" + check.pattern());
            }
            Matcher matcher = pattern.matcher(value);
            if (!matcher.find()) {
                return CheckOutcome.of(
                        check.requireMatch() ? Boolean.FALSE : Boolean.TRUE,
                        typeName + " key=" + check.key() + " value=" + value + " pattern=" + check.pattern() + " matched=false"
                );
            }
            if (!check.hasCondition()) {
                return CheckOutcome.of(
                        Boolean.TRUE,
                        typeName + " key=" + check.key() + " value=" + value + " pattern=" + check.pattern() + " matched=true"
                );
            }
            String resolvedCondition = applyFlexibleContext(player, payload, check.condition(), value, matcher);
            Boolean accepted = evaluateResolvedCondition(resolvedCondition);
            return new CheckOutcome(
                    accepted,
                    List.of(typeName + " key=" + check.key() + " value=" + value + " pattern=" + check.pattern()
                            + " condition=" + resolvedCondition + " accepted=" + accepted)
            );
        }
        if (!check.hasCondition()) {
            boolean accepted = Texts.isNotBlank(value);
            return CheckOutcome.of(
                    accepted,
                    typeName + " key=" + check.key() + " value=" + value + " exists=" + accepted
            );
        }
        String resolvedCondition = applyFlexibleContext(player, payload, check.condition(), value, null);
        Boolean accepted = evaluateResolvedCondition(resolvedCondition);
        return new CheckOutcome(
                accepted,
                List.of(typeName + " key=" + check.key() + " value=" + value + " condition=" + resolvedCondition + " accepted=" + accepted)
        );
    }

    private CheckOutcome evaluateLoreRegexCheck(Player player,
            PdcAttributePayload payload,
            List<String> loreLines,
            RuleCheck check) {
        Pattern pattern = compileRulePattern(check.pattern());
        if (pattern == null) {
            return CheckOutcome.invalid("lore_regex invalid_pattern=" + check.pattern());
        }
        if (loreLines == null || loreLines.isEmpty()) {
            return CheckOutcome.of(
                    check.requireMatch() ? Boolean.FALSE : Boolean.TRUE,
                    "lore_regex pattern=" + check.pattern() + " lore_empty=true"
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
            if (!check.hasCondition()) {
                return CheckOutcome.of(Boolean.TRUE, "lore_regex pattern=" + check.pattern() + " line=" + line + " matched=true");
            }
            String resolvedCondition = applyFlexibleContext(player, payload, check.condition(), line, matcher);
            Boolean accepted = evaluateResolvedCondition(resolvedCondition);
            traces.add("lore_regex pattern=" + check.pattern() + " line=" + line + " condition=" + resolvedCondition + " accepted=" + accepted);
            if (accepted == null) {
                sawInvalid = true;
                continue;
            }
            if (accepted) {
                return new CheckOutcome(Boolean.TRUE, traces);
            }
        }
        if (!matched) {
            return CheckOutcome.of(
                    check.requireMatch() ? Boolean.FALSE : Boolean.TRUE,
                    "lore_regex pattern=" + check.pattern() + " matched=false"
            );
        }
        if (sawInvalid) {
            return new CheckOutcome(null, traces);
        }
        return new CheckOutcome(Boolean.FALSE, traces);
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
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (Exception ignored) {
            return null;
        }
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
        var lore = ItemTextBridge.lore(itemMeta);
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (var line : lore) {
            String plain = Texts.normalizeWhitespace(MiniMessages.plain(line));
            if (Texts.isNotBlank(plain)) {
                lines.add(plain);
            }
        }
        return lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    private String resolvePlaceholders(Player player, PdcAttributePayload payload, String text) {
        String resolved = replaceTokenPattern(text, SOURCE_META_PATTERN, key -> payload.meta().getOrDefault(normalizeId(key), ""));
        resolved = replaceTokenPattern(resolved, SOURCE_ATTRIBUTE_PATTERN, key -> formatNumber(payload.attributes().get(normalizeId(key))));
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
            } catch (Exception ignored) {
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
        synchronized (DECIMAL_FORMAT) {
            return DECIMAL_FORMAT.format(value);
        }
    }

    private void mergeValues(Map<String, Double> target, Map<String, Double> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            target.merge(normalizeId(entry.getKey()), entry.getValue(), Double::sum);
        }
    }

    private List<String> addSource(List<String> current, String sourceId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(current == null ? List.of() : current);
        ids.add(normalizeId(sourceId));
        return new ArrayList<>(ids);
    }

    private List<String> removeSource(List<String> current, String sourceId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(current == null ? List.of() : current);
        ids.remove(normalizeId(sourceId));
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
            String normalized = normalizeId(entry);
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

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeCheckType(String type) {
        String normalized = normalizeId(type);
        return switch (normalized) {
            case "meta" ->
                "pdc_meta";
            case "attribute", "attr" ->
                "pdc_attribute";
            case "lore" ->
                "lore_regex";
            case "source" ->
                "source_id";
            default ->
                normalized;
        };
    }

    record PdcAttributeCollection(Map<String, Double> values, String sourceSignature) {

        PdcAttributeCollection {
            values = values == null ? Map.of() : Map.copyOf(values);
            sourceSignature = sourceSignature == null ? "" : sourceSignature;
        }
    }

    private record FilterOutcome(boolean accepted, List<String> resolvedConditions) {

        private FilterOutcome {
            resolvedConditions = resolvedConditions == null ? List.of() : List.copyOf(resolvedConditions);
        }
    }

    private record CheckOutcome(Boolean accepted, List<String> traces) {

        private CheckOutcome {
            traces = traces == null ? List.of() : List.copyOf(traces);
        }

        static CheckOutcome of(Boolean accepted, String trace) {
            return new CheckOutcome(accepted, Texts.isBlank(trace) ? List.of() : List.of(trace));
        }

        static CheckOutcome invalid(String trace) {
            return of(null, trace);
        }
    }
}
