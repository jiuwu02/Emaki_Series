package emaki.jiuwu.craft.gem.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class LegacyMatchBlockConverter {

    public static final String FIELD_ITEM_SOURCES = "item_sources";
    public static final String FIELD_SLOT_GROUPS = "slot_groups";
    public static final String FIELD_LORE_CONTAINS = "lore_contains";

    private LegacyMatchBlockConverter() {
    }

    public record LegacyMatchBlock(List<String> itemSources,
            List<String> slotGroups,
            List<String> loreContains) {

        public LegacyMatchBlock {
            itemSources = clean(itemSources);
            slotGroups = clean(slotGroups);
            loreContains = clean(loreContains);
        }

        public boolean empty() {
            return itemSources.isEmpty() && slotGroups.isEmpty() && loreContains.isEmpty();
        }

        public static LegacyMatchBlock none() {
            return new LegacyMatchBlock(List.of(), List.of(), List.of());
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

    public static @NotNull Conversion convert(@Nullable LegacyMatchBlock block) {
        LegacyMatchBlock source = block == null ? LegacyMatchBlock.none() : block;
        List<Map<String, Object>> parts = new ArrayList<>(loreMatchers(source.loreContains()));
        Map<String, List<String>> promoted = new LinkedHashMap<>();
        if (!source.itemSources().isEmpty()) {
            promoted.put(FIELD_ITEM_SOURCES, source.itemSources());
        }
        if (!source.slotGroups().isEmpty()) {
            promoted.put(FIELD_SLOT_GROUPS, source.slotGroups());
        }
        return new Conversion(combine(parts), promoted, List.of());
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
