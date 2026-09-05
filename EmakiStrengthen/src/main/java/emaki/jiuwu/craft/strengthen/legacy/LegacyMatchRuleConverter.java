package emaki.jiuwu.craft.strengthen.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class LegacyMatchRuleConverter {

    public static final String FIELD_SOURCE_IDS = "source_ids";
    public static final String FIELD_SOURCE_TYPES = "source_types";
    public static final String FIELD_SOURCE_PATTERNS = "source_patterns";
    public static final String FIELD_SLOT_GROUPS = "slot_groups";
    public static final String FIELD_LORE_CONTAINS = "lore_contains";
    public static final String FIELD_STATS_ANY = "stats_any";

    private LegacyMatchRuleConverter() {
    }

    public record LegacyMatchRule(List<String> sourceTypes,
            List<String> sourceIds,
            List<String> sourcePatterns,
            List<String> slotGroups,
            List<String> loreContains,
            List<String> statsAny) {

        public LegacyMatchRule {
            sourceTypes = clean(sourceTypes);
            sourceIds = clean(sourceIds);
            sourcePatterns = clean(sourcePatterns);
            slotGroups = clean(slotGroups);
            loreContains = clean(loreContains);
            statsAny = clean(statsAny);
        }

        public boolean empty() {
            return sourceTypes.isEmpty()
                    && sourceIds.isEmpty()
                    && sourcePatterns.isEmpty()
                    && slotGroups.isEmpty()
                    && loreContains.isEmpty()
                    && statsAny.isEmpty();
        }

        public static LegacyMatchRule none() {
            return new LegacyMatchRule(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record Conversion(@Nullable Map<String, Object> matcher,
            Map<String, List<String>> promotedFields,
            List<String> unconvertibleFields) {

        public Conversion {
            promotedFields = promotedFields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(promotedFields));
            unconvertibleFields = unconvertibleFields == null ? List.of() : List.copyOf(unconvertibleFields);
        }

        public boolean producesNothing() {
            return matcher == null && promotedFields.isEmpty();
        }

        public boolean complete() {
            return unconvertibleFields.isEmpty();
        }
    }

    public static @NotNull Conversion convert(@Nullable LegacyMatchRule rule) {
        LegacyMatchRule source = rule == null ? LegacyMatchRule.none() : rule;
        List<Map<String, Object>> parts = new ArrayList<>(loreMatchers(source.loreContains()));
        Map<String, List<String>> promoted = new LinkedHashMap<>();
        if (!source.slotGroups().isEmpty()) {
            promoted.put(FIELD_SLOT_GROUPS, source.slotGroups());
        }
        if (!source.statsAny().isEmpty()) {
            promoted.put(FIELD_STATS_ANY, source.statsAny());
        }
        List<String> patterns = new ArrayList<>(source.sourcePatterns());
        for (String sourceId : source.sourceIds()) {
            String anchored = anchoredSourcePattern(sourceId);
            if (!anchored.isEmpty() && !patterns.contains(anchored)) {
                patterns.add(anchored);
            }
        }
        if (!patterns.isEmpty()) {
            promoted.put(FIELD_SOURCE_PATTERNS, List.copyOf(patterns));
        }
        List<String> unconvertible = source.sourceTypes().isEmpty()
                ? List.of()
                : List.of(FIELD_SOURCE_TYPES);
        return new Conversion(combine(parts), promoted, unconvertible);
    }

    private static @NotNull String anchoredSourcePattern(@Nullable String sourceId) {
        String token = Texts.toStringSafe(sourceId).trim();
        return token.isEmpty() ? "" : "^" + Pattern.quote(token) + "$";
    }

    public static @NotNull List<Map<String, Object>> loreMatchers(@NotNull List<String> fragments) {
        List<Map<String, Object>> matchers = new ArrayList<>();
        for (String fragment : fragments) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "component");
            node.put("component", "lore");
            node.put("operator", "contains");
            node.put("value", fragment);
            matchers.add(node);
        }
        return matchers;
    }

    public static @Nullable Map<String, Object> combine(@NotNull List<Map<String, Object>> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "all_of");
        node.put("matchers", List.copyOf(parts));
        return node;
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String text = Texts.toStringSafe(value).trim();
            if (Texts.isNotBlank(text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
