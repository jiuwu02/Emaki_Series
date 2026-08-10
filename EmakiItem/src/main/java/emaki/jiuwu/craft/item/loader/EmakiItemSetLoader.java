package emaki.jiuwu.craft.item.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.model.ItemDirectoryConfig;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetLoreConfig;
import emaki.jiuwu.craft.item.model.ItemSetPieceDefinition;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;

public final class EmakiItemSetLoader {

    private final JavaPlugin plugin;
    private final Supplier<AppConfig> configSupplier;
    private volatile Snapshot snapshot = new Snapshot(0L, Map.of());

    public EmakiItemSetLoader(JavaPlugin plugin) {
        this(plugin, null);
    }

    public EmakiItemSetLoader(JavaPlugin plugin, Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
    }

    public int load() {
        return load(() -> true);
    }

    public int load(BooleanSupplier allowed) {
        if (!allowed(allowed)) {
            return snapshot.definitions().size();
        }
        File directory = plugin.getDataFolder().toPath().resolve("sets").toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create sets directory: " + directory.getPath());
        }
        Map<String, ItemSetDefinition> loaded = new LinkedHashMap<>();
        for (File file : files(directory)) {
            ItemSetDefinition definition = parse(YamlFiles.load(file), file.getPath());
            if (definition == null) {
                continue;
            }
            if (loaded.containsKey(definition.id())) {
                plugin.getLogger().warning("Duplicate EmakiItem set id '" + definition.id() + "' in " + file.getPath() + ", keeping first definition.");
                continue;
            }
            loaded.put(definition.id(), definition);
        }
        if (!allowed(allowed)) {
            return snapshot.definitions().size();
        }
        install(loaded);
        return loaded.size();
    }

    public ItemSetDefinition get(String id) {
        return snapshot.definitions().get(Texts.normalizeId(id));
    }

    public Map<String, ItemSetDefinition> all() {
        return snapshot.definitions();
    }

    public long generation() {
        return snapshot.generation();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private boolean allowed(BooleanSupplier allowed) {
        return allowed == null || allowed.getAsBoolean();
    }

    private synchronized void install(Map<String, ItemSetDefinition> loaded) {
        Snapshot current = snapshot;
        snapshot = new Snapshot(current.generation() + 1L, loaded);
    }

    private ItemSetDefinition parse(YamlSection root, String source) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        String id = Texts.normalizeId(root.getString("id"));
        if (Texts.isBlank(id)) {
            plugin.getLogger().warning("Skipping set definition " + source + ": invalid id.");
            return null;
        }
        return new ItemSetDefinition(
                id,
                root.getString("display_name", id),
                parsePieces(root.get("pieces")),
                parseThresholds(root.getSection("thresholds")),
                parseLore(root.getSection("lore"))
        );
    }

    private Map<String, ItemSetPieceDefinition> parsePieces(Object raw) {
        Map<String, ItemSetPieceDefinition> result = new LinkedHashMap<>();
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String pieceId = Texts.normalizeId(Texts.toStringSafe(entry.getKey()));
                Object value = ConfigNodes.toPlainData(entry.getValue());
                if (value instanceof Map<?, ?> pieceMap) {
                    Object itemRaw = pieceMap.containsKey("item") ? pieceMap.get("item") : pieceId;
                    Object slotRaw = pieceMap.containsKey("slot") ? pieceMap.get("slot") : pieceId;
                    Object displayRaw = pieceMap.containsKey("display") ? pieceMap.get("display") : pieceId;
                    String itemId = Texts.normalizeId(Texts.toStringSafe(itemRaw));
                    String slot = Texts.normalizeId(Texts.toStringSafe(slotRaw));
                    String display = Texts.toStringSafe(displayRaw);
                    putPiece(result, new ItemSetPieceDefinition(pieceId, itemId, slot, display));
                } else {
                    String itemId = Texts.normalizeId(Texts.toStringSafe(value));
                    putPiece(result, new ItemSetPieceDefinition(pieceId, itemId, pieceId, pieceId));
                }
            }
            return result;
        }
        for (String entry : Texts.asStringList(plain)) {
            String id = Texts.normalizeId(entry);
            putPiece(result, new ItemSetPieceDefinition(id, id, id, id));
        }
        return result;
    }

    private void putPiece(Map<String, ItemSetPieceDefinition> result, ItemSetPieceDefinition piece) {
        if (piece != null && Texts.isNotBlank(piece.pieceId())) {
            result.put(piece.pieceId(), piece);
        }
    }

    private List<ItemSetThreshold> parseThresholds(YamlSection section) {
        if (section == null) {
            return List.of();
        }
        List<ItemSetThreshold> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            int required = Numbers.tryParseInt(key, 0);
            YamlSection threshold = section.getSection(key);
            if (required <= 0 || threshold == null) {
                continue;
            }
            List<Map<?, ?>> effects = threshold.getMapList("effects");
            result.add(new ItemSetThreshold(
                    required,
                    normalizedList(threshold.get("lore")),
                    thresholdAttributes(threshold, effects),
                    thresholdSkills(threshold, effects),
                    thresholdActions(threshold, effects, "name_action", "name_actions", "name_action"),
                    thresholdActions(threshold, effects, "lore_action", "lore_actions", "lore_action"),
                    List.of()
            ));
        }
        return result;
    }

    private Map<String, Double> thresholdAttributes(YamlSection threshold, List<Map<?, ?>> effects) {
        Map<String, Double> result = new LinkedHashMap<>(toDoubleMap(threshold.get("ea_attributes")));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"ea_attribute".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            result.putAll(toDoubleMap(firstNonNull(ConfigNodes.get(effect, "ea_attributes"), ConfigNodes.get(effect, "attributes"))));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<String> thresholdSkills(YamlSection threshold, List<Map<?, ?>> effects) {
        LinkedHashSet<String> result = new LinkedHashSet<>(normalizedList(threshold.get("es_skills")));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            result.addAll(normalizedList(firstNonNull(ConfigNodes.get(effect, "es_skills"), ConfigNodes.get(effect, "es_skill"))));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private Object thresholdActions(YamlSection threshold, List<Map<?, ?>> effects, String effectType, String topKey, String effectKey) {
        List<Object> actions = new ArrayList<>();
        appendActions(actions, threshold.get(topKey));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !effectType.equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            appendActions(actions, ConfigNodes.get(effect, topKey));
            appendActions(actions, ConfigNodes.get(effect, effectKey));
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    private void appendActions(List<Object> actions, Object raw) {
        if (actions == null || raw == null) {
            return;
        }
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    actions.add(entry);
                }
            }
            return;
        }
        actions.add(plain);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ItemSetLoreConfig parseLore(YamlSection section) {
        if (section == null) {
            return ItemSetLoreConfig.defaults();
        }
        return new ItemSetLoreConfig(
                section.getString("header", ""),
                section.getString("equipped_format", ""),
                section.getString("missing_format", ""),
                section.getString("active_threshold_format", ""),
                section.getString("inactive_threshold_format", ""),
                section.getString("separator", "")
        );
    }

    private Map<String, Double> toDoubleMap(Object raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            Double value = Numbers.tryParseDouble(entry.getValue(), null);
            if (Texts.isNotBlank(entry.getKey()) && value != null) {
                result.put(Texts.normalizeId(entry.getKey()), value);
            }
        }
        return result;
    }

    private List<String> normalizedList(Object raw) {
        List<String> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            if (Texts.isNotBlank(entry)) {
                result.add(entry.trim());
            }
        }
        return result;
    }

    private File[] files(File directory) {
        if (directory == null || !directory.exists()) {
            return new File[0];
        }
        List<File> result = new ArrayList<>();
        collect(directory, directory, 1, maxDepth(), result);
        result.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        return result.toArray(File[]::new);
    }

    private void collect(File directory, File root, int depth, int maxDepth, List<File> sink) {
        File[] entries = directory.listFiles(file -> file.isDirectory()
                || file.getName().endsWith(".yml")
                || file.getName().endsWith(".yaml"));
        if (entries == null) {
            return;
        }
        Arrays.sort(entries, (left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        for (File entry : entries) {
            if (!entry.isDirectory()) {
                sink.add(entry);
                continue;
            }
            if (depth >= maxDepth) {
                plugin.getLogger().warning("Skipping EmakiItem set directory " + relativize(entry, root)
                        + ": nesting exceeds data_directories.max_depth=" + maxDepth + ".");
                continue;
            }
            collect(entry, root, depth + 1, maxDepth, sink);
        }
    }

    private String relativize(File file, File root) {
        String path = file.getPath();
        String prefix = root.getPath();
        return path.startsWith(prefix) ? root.getName() + path.substring(prefix.length()) : path;
    }

    private int maxDepth() {
        AppConfig config = configSupplier == null ? null : configSupplier.get();
        return config == null ? ItemDirectoryConfig.DEFAULT_MAX_DEPTH : config.directories().maxDepth();
    }

    public record Snapshot(long generation, Map<String, ItemSetDefinition> definitions) {

        public Snapshot {
            generation = Math.max(0L, generation);
            definitions = definitions == null || definitions.isEmpty() ? Map.of() : Map.copyOf(definitions);
        }

        public ItemSetDefinition get(String id) {
            return definitions.get(Texts.normalizeId(id));
        }
    }
}
