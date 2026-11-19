package emaki.jiuwu.craft.kits.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.config.KitIds;

public final class KitYamlValidator implements EditorValidator {

    private static final Set<String> TYPES = Set.of(
            "join", "invite", "daily", "weekly", "monthly", "cdk", "choice", "generic");
    private static final Set<String> REWARD_TYPES = Set.of("item", "money", "nested_kit", "action");
    private static final Set<String> LIMIT_MODES = Set.of("always", "range", "cron");
    private static final Set<String> NUMERIC_KEYS = Set.of(
            "max_claims_total", "missed_window_seconds", "max_binds_per_invitee",
            "per_player_limit", "min_select", "max_select", "price", "max_purchases",
            "max_opens", "duration_seconds", "count", "amount", "weight");

    private final EmakiKitsPlugin plugin;

    public KitYamlValidator(EmakiKitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String validate(YamlTextDocument candidate, String docId, Path source) {
        YamlSection parsed;
        try {
            parsed = YamlFiles.load(candidate.text());
            if (parsed == null) {
                return "unparseable_yaml";
            }
        } catch (RuntimeException failure) {
            return "unparseable_yaml";
        }
        String numberIssue = numericIssues(parsed.asMap());
        if (numberIssue != null) {
            return numberIssue;
        }
        String id = KitIds.normalize(str(candidate.value("id")));
        if (!KitIds.valid(id)) {
            return "error.config.invalid_id";
        }
        if (docId != null && !docId.isEmpty() && !docId.equals(id)) {
            return "id_changed";
        }
        String type = str(candidate.value("type")).toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            return "error.config.unknown_type";
        }
        String permission = str(candidate.value("permission"));
        if (candidate.value("permission") != null && !permission.startsWith("emakikits.")) {
            return "error.config.permission_prefix";
        }
        boolean cyclic = type.equals("daily") || type.equals("weekly") || type.equals("monthly");
        if (cyclic) {
            String zone = str(candidate.value("cycle", "timezone"));
            if (!zone.isBlank() && !validZone(zone)) {
                return "error.config.invalid_timezone";
            }
        }
        if (type.equals("cdk")) {
            int codeCount = mapOrListSize(candidate.value("cdk", "codes"));
            if (codeCount == 0) {
                return "error.config.cdk_codes_empty";
            }
            if (codeCount > maxCodesPerKit()) {
                return "error.config.cdk_codes_overflow";
            }
        }
        if (type.equals("choice")) {
            int optionCount = mapValueCount(candidate.value("choice", "options"));
            int minSelect = intVal(candidate.value("choice", "min_select"), 1);
            int maxSelect = intVal(candidate.value("choice", "max_select"), 1);
            if (optionCount == 0 || minSelect < 0 || maxSelect < minSelect) {
                return "error.config.choice_invalid";
            }
            boolean hasGui = candidate.value("choice", "gui") != null
                    || !str(candidate.value("choice", "gui_template")).isBlank();
            if (!hasGui) {
                return "error.config.choice_gui_missing";
            }
        }
        boolean purchaseEnabled = boolVal(candidate.value("purchase", "enabled"), false);
        if (purchaseEnabled
                && doubleVal(candidate.value("purchase", "price"), 0D) > 0D
                && intVal(candidate.value("purchase", "max_purchases"), 0) == 0) {
            return "error.config.purchase_limit_missing";
        }
        String milestoneIssue = validateMilestones(candidate.value("open", "milestones"));
        if (milestoneIssue != null) {
            return milestoneIssue;
        }
        List<List<Object>> rewardLists = collectRewardLists(candidate);
        for (List<Object> list : rewardLists) {
            for (Object entry : list) {
                String issue = validateRewardEntry(entry);
                if (issue != null) {
                    return issue;
                }
            }
        }
        boolean weighted = boolVal(candidate.value("rewards", "weighted"), false);
        List<Object> random = listOf(candidate.value("rewards", "random"));
        if (weighted) {
            if (countMaps(random) == 0) {
                return "error.config.invalid_weight";
            }
            for (Object entry : random) {
                if (entry instanceof Map<?, ?> map && intVal(map.get("weight"), 0) <= 0) {
                    return "error.config.invalid_weight";
                }
            }
        }
        Object pityNode = candidate.value("rewards", "pity");
        if (pityNode instanceof Map<?, ?> pity) {
            String guaranteed = str(pity.get("guaranteed"));
            if (guaranteed.isBlank()) {
                return "error.config.pity_guaranteed_missing";
            }
            if (intVal(pity.get("count"), 1) < 1) {
                return "error.config.pity_count_invalid";
            }
            boolean known = random.stream().anyMatch(entry ->
                    entry instanceof Map<?, ?> map && guaranteed.equals(str(map.get("id"))));
            if (!known) {
                return "error.config.pity_guaranteed_unknown";
            }
            if (!weighted) {
                return "error.config.pity_requires_weighted";
            }
        }
        if (countMaps(listOf(candidate.value("rewards", "fixed"))) == 0
                && countMaps(random) == 0
                && mapValueCount(candidate.value("choice", "options")) == 0) {
            return "error.config.rewards_empty";
        }
        String limitIssue = validateLimit(candidate);
        if (limitIssue != null) {
            return limitIssue;
        }
        return validateNested(candidate, id, source);
    }

    private String validateMilestones(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        for (Object key : map.keySet()) {
            try {
                if (Integer.parseInt(str(key).trim()) <= 0) {
                    return "error.config.milestone_invalid";
                }
            } catch (NumberFormatException failure) {
                return "error.config.milestone_invalid";
            }
        }
        return null;
    }

    private String validateRewardEntry(Object entry) {
        if (!(entry instanceof Map<?, ?> map)) {
            return null;
        }
        String kind = str(map.get("type")).toLowerCase(Locale.ROOT);
        if (!REWARD_TYPES.contains(kind)) {
            return "error.config.reward_type";
        }
        if (intVal(map.get("weight"), 0) < 0) {
            return "error.config.invalid_weight";
        }
        if (kind.equals("item") && str(map.get("source")).isBlank()) {
            return "error.config.item_source_missing";
        }
        if (kind.equals("action") && str(map.get("action")).isBlank()) {
            return "error.config.action_empty";
        }
        if (kind.equals("nested_kit") && str(map.get("kit_id")).isBlank()) {
            return "error.config.nested_kit_missing";
        }
        return null;
    }

    private String validateLimit(YamlTextDocument candidate) {
        boolean enabled = boolVal(candidate.value("limit", "enabled"), false);
        if (!enabled) {
            return null;
        }
        String mode = str(candidate.value("limit", "mode")).toLowerCase(Locale.ROOT);
        if (mode.isBlank()) {
            mode = "always";
        }
        if (!LIMIT_MODES.contains(mode)) {
            return "error.config.limit_mode";
        }
        if (mode.equals("range")) {
            Long start = parseEpoch(str(candidate.value("limit", "start")));
            Long end = parseEpoch(str(candidate.value("limit", "end")));
            if (start == null || end == null || start > end) {
                return "error.config.limit_range";
            }
        }
        if (mode.equals("cron")) {
            String cron = str(candidate.value("limit", "cron"));
            if (!validCron(cron)) {
                return "error.config.limit_cron";
            }
            if (longVal(candidate.value("limit", "duration_seconds"), 0L) <= 0L) {
                return "error.config.limit_duration";
            }
        }
        return null;
    }

    private String validateNested(YamlTextDocument candidate, String id, Path source) {
        Path directory = source == null ? null : source.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> files = children
                    .filter(Files::isRegularFile)
                    .filter(KitYamlValidator::isYaml)
                    .toList();
            for (Path file : files) {
                try {
                    YamlSection yaml = YamlFiles.load(file.toFile());
                    if (yaml == null) {
                        continue;
                    }
                    String fileId = KitIds.normalize(yaml.getString("id", ""));
                    if (fileId.isEmpty()) {
                        fileId = stem(file);
                    }
                    graph.put(fileId, nestedIds(yaml));
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException failure) {
            return null;
        }
        Set<String> ownEdges = new LinkedHashSet<>();
        for (List<Object> list : collectRewardLists(candidate)) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    String target = KitIds.normalize(str(map.get("kit_id")));
                    if (str(map.get("type")).toLowerCase(Locale.ROOT).equals("nested_kit") && !target.isEmpty()) {
                        ownEdges.add(target);
                    }
                }
            }
        }
        for (String edge : ownEdges) {
            if (edge.equals(id)) {
                return "error.config.nested_self";
            }
        }
        graph.put(id, ownEdges);
        return walk(id, graph, new HashSet<>(), new ArrayDeque<>());
    }

    private static String walk(String node, Map<String, Set<String>> graph,
                               Set<String> finished, Deque<String> stack) {
        if (finished.contains(node)) {
            return null;
        }
        if (stack.contains(node)) {
            return "error.config.nested_cycle";
        }
        stack.push(node);
        for (String edge : graph.getOrDefault(node, Set.of())) {
            if (edge.equals(node)) {
                return "error.config.nested_self";
            }
            if (!graph.containsKey(edge)) {
                return "error.config.nested_missing";
            }
            String issue = walk(edge, graph, finished, stack);
            if (issue != null) {
                return issue;
            }
        }
        stack.pop();
        finished.add(node);
        return null;
    }

    private static Set<String> nestedIds(YamlSection yaml) {
        Set<String> ids = new LinkedHashSet<>();
        collectNested(yaml.getMapList("rewards.fixed"), ids);
        collectNested(yaml.getMapList("rewards.random"), ids);
        YamlSection options = yaml.getSection("choice.options");
        if (options != null) {
            for (String key : options.getKeys(false)) {
                YamlSection option = options.getSection(key);
                if (option != null) {
                    collectNested(option.getMapList("rewards"), ids);
                }
            }
        }
        YamlSection milestones = yaml.getSection("open.milestones");
        if (milestones != null) {
            for (String key : milestones.getKeys(false)) {
                YamlSection milestone = milestones.getSection(key);
                if (milestone != null) {
                    collectNested(milestone.getMapList("rewards"), ids);
                }
            }
        }
        return ids;
    }

    private static void collectNested(List<Map<?, ?>> raw, Set<String> ids) {
        for (Map<?, ?> entry : raw) {
            if ("nested_kit".equalsIgnoreCase(str(entry.get("type")))) {
                String target = KitIds.normalize(str(entry.get("kit_id")));
                if (!target.isEmpty()) {
                    ids.add(target);
                }
            }
        }
    }

    private List<List<Object>> collectRewardLists(YamlTextDocument candidate) {
        List<List<Object>> lists = new ArrayList<>();
        lists.add(listOf(candidate.value("rewards", "fixed")));
        lists.add(listOf(candidate.value("rewards", "random")));
        Object options = candidate.value("choice", "options");
        if (options instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                lists.add(listOf(candidate.value("choice", "options", str(key), "rewards")));
            }
        }
        Object milestones = candidate.value("open", "milestones");
        if (milestones instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                lists.add(listOf(candidate.value("open", "milestones", str(key), "rewards")));
            }
        }
        return lists;
    }

    private int maxCodesPerKit() {
        try {
            return Math.max(1, plugin.appConfig().cdk().maxCodesPerKit());
        } catch (RuntimeException failure) {
            return 10000;
        }
    }

    private static List<Object> listOf(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    private static int countMaps(List<Object> list) {
        int count = 0;
        for (Object entry : list) {
            if (entry instanceof Map<?, ?>) {
                count++;
            }
        }
        return count;
    }

    private static int mapValueCount(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return 0;
        }
        int count = 0;
        for (Object entry : map.values()) {
            if (entry instanceof Map<?, ?>) {
                count++;
            }
        }
        return count;
    }

    private static int mapOrListSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return 0;
    }

    private static boolean validZone(String raw) {
        try {
            ZoneId.of(raw);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean validCron(String raw) {
        try {
            emaki.jiuwu.craft.corelib.schedule.cron.CronExpression.parse(raw);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static Long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim()).toEpochMilli();
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static String numericIssues(Map<?, ?> root) {
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (NUMERIC_KEYS.contains(key) && value != null && !blank(value) && !numericValue(value)) {
                return "error.config.number_invalid";
            }
            String nested = null;
            if (value instanceof Map<?, ?> map) {
                nested = numericIssues(map);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        nested = numericIssues(map);
                        if (nested != null) {
                            break;
                        }
                    }
                }
            }
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static boolean blank(Object value) {
        return value instanceof String text && text.isBlank();
    }

    private static boolean numericValue(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String text) {
            try {
                Double.parseDouble(text.trim());
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String stem(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longVal(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double doubleVal(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
