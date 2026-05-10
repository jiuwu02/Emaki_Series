package emaki.jiuwu.craft.item.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetLoreConfig;
import emaki.jiuwu.craft.item.model.ItemSetPieceDefinition;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;

public final class EmakiItemSetLoader {

    private final JavaPlugin plugin;
    private volatile Map<String, ItemSetDefinition> definitions = Map.of();

    public EmakiItemSetLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
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
        definitions = new ConcurrentHashMap<>(loaded);
        return definitions.size();
    }

    public ItemSetDefinition get(String id) {
        return definitions.get(Texts.normalizeId(id));
    }

    public Map<String, ItemSetDefinition> all() {
        return Map.copyOf(definitions);
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
            result.add(new ItemSetThreshold(
                    required,
                    normalizedList(threshold.get("lore")),
                    toDoubleMap(threshold.get("ea_attributes")),
                    normalizedList(threshold.get("es_skills"))
            ));
        }
        return result;
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
        File[] files = directory.listFiles(file -> file.isDirectory()
                || file.getName().endsWith(".yml")
                || file.getName().endsWith(".yaml"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                result.addAll(Arrays.asList(files(file)));
            } else {
                result.add(file);
            }
        }
        return result.toArray(File[]::new);
    }
}
