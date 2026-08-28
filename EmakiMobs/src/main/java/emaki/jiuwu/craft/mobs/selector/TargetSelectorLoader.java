package emaki.jiuwu.craft.mobs.selector;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class TargetSelectorLoader {

    private static final String FILE_NAME = "target_selectors.yml";
    private static final int MAX_EXPRESSION_LENGTH = 256;
    private static final int MAX_EXPRESSION_DEPTH = 10;
    private static final Pattern DANGEROUS_EXPRESSION = Pattern.compile("[`$\\\\]");
    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%\\s]+)%");

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final List<String> issues = new ArrayList<>();

    private TargetSelectorConfig config = TargetSelectorConfig.empty();
    private boolean blockingIssues;

    public TargetSelectorLoader(JavaPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.itemSourceService = itemSourceService;
    }

    public File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    public TargetSelectorConfig config() {
        return config;
    }

    public List<String> issues() {
        return List.copyOf(issues);
    }

    public boolean hasBlockingIssues() {
        return blockingIssues;
    }

    public int load() {
        issues.clear();
        blockingIssues = false;
        YamlSection root;
        try {
            root = YamlFiles.load(file());
        } catch (YamlLoadException exception) {
            warn("loader.target_selectors_load_failed", Map.of(
                    "file", FILE_NAME,
                    "error", Texts.toStringSafe(exception.getMessage())));
            blockingIssues = true;
            config = TargetSelectorConfig.empty();
            return 0;
        }
        if (root == null) {
            warn("loader.target_selectors_load_failed", Map.of(
                    "file", FILE_NAME,
                    "error", "empty document"));
            blockingIssues = true;
            config = TargetSelectorConfig.empty();
            return 0;
        }

        Map<String, EquipmentWeightTable> tables = parseEquipmentTables(root);
        Map<String, String> expressionIds = new LinkedHashMap<>();
        Map<String, String> expressions = new LinkedHashMap<>();
        Set<String> referencedTables = new LinkedHashSet<>();
        Map<String, SelectorDefinition> selectors = parseSelectors(
                root, tables, expressionIds, expressions, referencedTables);
        int interval = root.getInt("snapshot_interval_ticks", 20);
        int playersPerTick = root.getInt("snapshot_players_per_tick", 20);
        config = new TargetSelectorConfig(interval, playersPerTick, tables, selectors,
                expressions, referencedTables);
        return selectors.size();
    }

    private Map<String, EquipmentWeightTable> parseEquipmentTables(YamlSection root) {
        YamlSection section = root.getSection("equipment_tables");
        if (section == null) {
            return Map.of();
        }
        Map<String, EquipmentWeightTable> tables = new LinkedHashMap<>();
        for (String rawId : section.getKeys(false)) {
            YamlSection entry = section.getSection(rawId);
            if (entry == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Set<EquipmentSlot> slots = parseSlots(id, entry.getStringList("slots"));
            double defaultWeight = entry.getDouble("default_weight", 0D);
            Map<ItemSourceRef, Double> weights = parseWeights(id, entry.getSection("weights"));
            tables.put(id, new EquipmentWeightTable(id, slots, defaultWeight, weights));
        }
        return Map.copyOf(tables);
    }

    private Set<EquipmentSlot> parseSlots(String tableId, List<String> configured) {
        Set<EquipmentSlot> slots = new LinkedHashSet<>();
        for (String raw : configured) {
            EquipmentSlot slot = parseSlot(raw);
            if (slot == null) {
                warn("loader.target_selector_invalid_slot", Map.of(
                        "table", tableId,
                        "slot", Texts.toStringSafe(raw)));
                continue;
            }
            slots.add(slot);
        }
        return Set.copyOf(slots);
    }

    private EquipmentSlot parseSlot(String raw) {
        String normalized = normalizeId(raw);
        return switch (normalized) {
            case "main_hand", "mainhand" -> EquipmentSlot.HAND;
            case "off_hand", "offhand" -> EquipmentSlot.OFF_HAND;
            default -> {
                try {
                    yield EquipmentSlot.valueOf(normalized.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    yield null;
                }
            }
        };
    }

    private Map<ItemSourceRef, Double> parseWeights(String tableId, YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<ItemSourceRef, Double> weights = new LinkedHashMap<>();
        for (String shorthand : section.getKeys(false)) {
            ItemSourceRef ref = parseItemSource(tableId, shorthand);
            Object rawWeight = section.get(shorthand);
            if (ref == null || !(rawWeight instanceof Number number)) {
                continue;
            }
            weights.put(ref, number.doubleValue());
        }
        return Map.copyOf(weights);
    }

    private ItemSourceRef parseItemSource(String tableId, String shorthand) {
        String canonical = canonicalShorthand(shorthand);
        ItemSourceRef ref;
        try {
            ref = ItemSourceUtil.parseShorthand(canonical);
        } catch (RuntimeException exception) {
            ref = null;
        }
        if (ref == null || ref.vanilla() && ItemSourceUtil.resolveVanillaMaterial(ref.identifier()) == null) {
            warn("loader.target_selector_invalid_item", Map.of(
                    "table", tableId,
                    "item", Texts.toStringSafe(shorthand)));
            return null;
        }
        var probe = itemSourceService.probe(ref);
        if (!probe.ready()) {
            warn("loader.target_selector_invalid_item", Map.of(
                    "table", tableId,
                    "item", Texts.toStringSafe(shorthand)));
            return null;
        }
        return ref;
    }

    private String canonicalShorthand(String shorthand) {
        String normalized = Texts.toStringSafe(shorthand).trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("minecraft:")) {
            return "minecraft-" + normalized.substring("minecraft:".length());
        }
        if (lower.startsWith("emakiitem:")) {
            return "emakiitem-" + normalized.substring("emakiitem:".length());
        }
        return normalized;
    }

    private Map<String, SelectorDefinition> parseSelectors(YamlSection root,
            Map<String, EquipmentWeightTable> tables,
            Map<String, String> expressionIds,
            Map<String, String> expressions,
            Set<String> referencedTables) {
        YamlSection section = root.getSection("selectors");
        if (section == null) {
            warn("loader.target_selectors_section_missing", Map.of("file", FILE_NAME));
            blockingIssues = true;
            return Map.of();
        }
        Map<String, SelectorDefinition> selectors = new LinkedHashMap<>();
        for (String rawId : section.getKeys(false)) {
            YamlSection entry = section.getSection(rawId);
            if (entry == null) {
                continue;
            }
            String id = normalizeId(rawId);
            SelectorMode mode = parseMode(id, entry.getString("mode", "highest"));
            if (mode == null) {
                continue;
            }
            Double configuredRange = entry.getDouble("range");
            double range = configuredRange == null ? -1D : Math.max(0D, configuredRange);
            ConditionBlock filter = ConditionBlock.fromConfig(entry.get("filter"));
            List<ScoreTerm> score = parseScore(id, entry.getMapList("score"), tables,
                    expressionIds, expressions, referencedTables);
            selectors.put(id, new SelectorDefinition(id, range, mode, filter, score));
        }
        return Map.copyOf(selectors);
    }

    private SelectorMode parseMode(String selectorId, String raw) {
        try {
            return SelectorMode.valueOf(normalizeId(raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warn("loader.target_selector_invalid_mode", Map.of(
                    "selector", selectorId,
                    "mode", Texts.toStringSafe(raw)));
            return null;
        }
    }

    private List<ScoreTerm> parseScore(String selectorId,
            List<Map<?, ?>> configured,
            Map<String, EquipmentWeightTable> tables,
            Map<String, String> expressionIds,
            Map<String, String> expressions,
            Set<String> referencedTables) {
        List<ScoreTerm> terms = new ArrayList<>();
        for (Map<?, ?> raw : configured) {
            String type = normalizeId(raw.get("type"));
            double factor = raw.get("factor") instanceof Number number ? number.doubleValue() : 1D;
            if (!Double.isFinite(factor)) {
                warn("loader.target_selector_invalid_score", Map.of(
                        "selector", selectorId,
                        "type", type));
                continue;
            }
            ScoreTerm term = switch (type) {
                case "threat" -> new ScoreTerm.ThreatTerm(factor);
                case "distance" -> new ScoreTerm.DistanceTerm(factor);
                case "health" -> new ScoreTerm.HealthTerm(factor);
                case "equipment" -> parseEquipmentTerm(selectorId, raw, factor, tables, referencedTables);
                case "expression" -> parseExpressionTerm(
                        selectorId, raw, factor, expressionIds, expressions);
                default -> {
                    warn("loader.target_selector_invalid_score", Map.of(
                            "selector", selectorId,
                            "type", type));
                    yield null;
                }
            };
            if (term == null) {
                continue;
            }
            terms.add(term);
        }
        return List.copyOf(terms);
    }

    private ScoreTerm parseEquipmentTerm(String selectorId,
            Map<?, ?> raw,
            double factor,
            Map<String, EquipmentWeightTable> tables,
            Set<String> referencedTables) {
        String tableId = normalizeId(raw.get("table"));
        if (!tables.containsKey(tableId)) {
            warn("loader.target_selector_unknown_table", Map.of(
                    "selector", selectorId,
                    "table", tableId));
            return null;
        }
        referencedTables.add(tableId);
        return new ScoreTerm.EquipmentTerm(tableId, factor);
    }

    private ScoreTerm parseExpressionTerm(String selectorId,
            Map<?, ?> raw,
            double factor,
            Map<String, String> expressionIds,
            Map<String, String> expressions) {
        String expression = Texts.toStringSafe(raw.get("value")).trim();
        if (!validExpression(expression)) {
            warn("loader.target_selector_invalid_expression", Map.of(
                    "selector", selectorId,
                    "expression", expression));
            return null;
        }
        String expressionId = expressionIds.computeIfAbsent(expression,
                ignored -> "expression_" + (expressionIds.size() + 1));
        expressions.putIfAbsent(expressionId, expression);
        return new ScoreTerm.ExpressionTerm(expressionId, factor);
    }

    private boolean validExpression(String expression) {
        if (expression.isBlank() || expression.length() > MAX_EXPRESSION_LENGTH
                || DANGEROUS_EXPRESSION.matcher(expression).find()) {
            return false;
        }
        int depth = 0;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (current == '(' && ++depth > MAX_EXPRESSION_DEPTH) {
                return false;
            }
            if (current == ')' && --depth < 0) {
                return false;
            }
        }
        if (depth != 0) {
            return false;
        }
        String prepared = PLACEHOLDER.matcher(expression).replaceAll("0");
        return ExpressionEngine.evaluateNumericDetailed(prepared).success();
    }

    private String normalizeId(Object raw) {
        return Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
    }

    private void warn(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            issues.add(messageService.message(key, replacements));
            messageService.warning(key, replacements);
            return;
        }
        issues.add(key + " " + replacements);
        plugin.getLogger().warning(key + " " + replacements);
    }
}
