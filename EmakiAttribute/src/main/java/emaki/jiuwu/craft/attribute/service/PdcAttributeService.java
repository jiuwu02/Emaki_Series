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
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;
import emaki.jiuwu.craft.attribute.model.PdcReadRule;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
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
            FilterOutcome outcome = evaluateRule(player, payload, rule);
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

    private FilterOutcome evaluateRule(Player player, PdcAttributePayload payload, PdcReadRule rule) {
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

    private String resolvePlaceholders(Player player, PdcAttributePayload payload, String text) {
        String resolved = replaceTokenPattern(text, SOURCE_META_PATTERN, key -> payload.meta().getOrDefault(normalizeId(key), ""));
        resolved = replaceTokenPattern(resolved, SOURCE_ATTRIBUTE_PATTERN, key -> formatNumber(payload.attributes().get(normalizeId(key))));
        resolved = resolved.replace("%source_id%", payload.sourceId());
        if (player != null && Texts.isNotBlank(resolved) && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
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
}
