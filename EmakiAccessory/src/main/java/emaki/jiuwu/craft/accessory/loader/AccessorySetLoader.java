package emaki.jiuwu.craft.accessory.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.accessory.model.AccessorySetDefinition;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

/**
 * Loads {@code sets/**.yml} into {@link AccessorySetDefinition}s.
 *
 * <p>The file shape mirrors EmakiItem's {@code sets/example_set.yml} so server owners reuse a syntax
 * they already know, with one difference: {@code slot} names an accessory part or slot instance instead
 * of a Bukkit equipment slot.
 *
 * <p>A set whose pieces or thresholds fail to parse is skipped with a recorded issue rather than
 * aborting the directory, so one bad file does not cost an administrator every other set.
 */
public final class AccessorySetLoader extends YamlDirectoryLoader<AccessorySetDefinition> {

    /**
     * Creates the loader.
     *
     * @param plugin the owning plugin
     */
    public AccessorySetLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "sets";
    }

    @Override
    protected String typeName() {
        return "accessory set";
    }

    @Override
    protected String idOf(AccessorySetDefinition value) {
        return value == null ? null : value.setId();
    }

    @Override
    protected AccessorySetDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String setId = Texts.normalizeId(configuration.getString("id", ""));
        if (Texts.isBlank(setId)) {
            return null;
        }
        Map<String, AccessorySetDefinition.Piece> pieces = parsePieces(file, setId, configuration.getSection("pieces"));
        if (pieces.isEmpty()) {
            issue("accessory.set_without_pieces", Map.of("file", file.getName(), "set", setId));
            return null;
        }
        Map<Integer, AccessorySetDefinition.Threshold> thresholds =
                parseThresholds(file, setId, configuration.getSection("thresholds"), pieces.size());
        return new AccessorySetDefinition(
                setId,
                Texts.toStringSafe(configuration.getString("display_name", "")),
                pieces,
                thresholds
        );
    }

    private Map<String, AccessorySetDefinition.Piece> parsePieces(File file, String setId, YamlSection section) {
        Map<String, AccessorySetDefinition.Piece> pieces = new LinkedHashMap<>();
        if (section == null) {
            return pieces;
        }
        for (String key : section.getKeys(false)) {
            YamlSection entry = section.getSection(key);
            if (entry == null) {
                continue;
            }
            String pieceId = Texts.normalizeId(key);
            String itemId = Texts.normalizeId(entry.getString("item", ""));
            if (Texts.isBlank(pieceId) || Texts.isBlank(itemId)) {
                issue("accessory.set_piece_invalid", Map.of(
                        "file", file.getName(),
                        "set", setId,
                        "piece", Texts.toStringSafe(key)
                ));
                continue;
            }
            pieces.put(pieceId, new AccessorySetDefinition.Piece(
                    pieceId,
                    itemId,
                    entry.getString("slot", ""),
                    entry.getString("display", "")
            ));
        }
        return pieces;
    }

    private Map<Integer, AccessorySetDefinition.Threshold> parseThresholds(File file,
            String setId,
            YamlSection section,
            int totalPieces) {
        Map<Integer, AccessorySetDefinition.Threshold> thresholds = new LinkedHashMap<>();
        if (section == null) {
            return thresholds;
        }
        for (String key : section.getKeys(false)) {
            int requiredPieces;
            try {
                requiredPieces = Integer.parseInt(Texts.trim(key));
            } catch (NumberFormatException exception) {
                issue("accessory.set_threshold_not_numeric", Map.of(
                        "file", file.getName(),
                        "set", setId,
                        "threshold", Texts.toStringSafe(key)
                ));
                continue;
            }
            if (requiredPieces < 1 || requiredPieces > totalPieces) {
                issue("accessory.set_threshold_out_of_range", Map.of(
                        "file", file.getName(),
                        "set", setId,
                        "threshold", Integer.toString(requiredPieces),
                        "total", Integer.toString(totalPieces)
                ));
                continue;
            }
            YamlSection entry = section.getSection(key);
            if (entry == null) {
                continue;
            }
            Map<String, Double> attributes = new LinkedHashMap<>();
            List<String> skills = new ArrayList<>();
            collectEffects(entry, attributes, skills);
            thresholds.put(requiredPieces,
                    new AccessorySetDefinition.Threshold(requiredPieces, attributes, skills));
        }
        return thresholds;
    }

    /**
     * Reads the {@code effects} list of one threshold.
     *
     * <p>Effect entries are keyed by {@code type} exactly like EmakiItem's set effects, so the same
     * {@code ea_attribute} / {@code es_skill} vocabulary applies. Unknown types are ignored rather than
     * rejected: a future effect type must not invalidate an existing set file.
     */
    private void collectEffects(YamlSection threshold, Map<String, Double> attributes, List<String> skills) {
        for (Map<?, ?> raw : threshold.getMapList("effects")) {
            if (raw == null) {
                continue;
            }
            Object attributeNode = raw.get("ea_attributes");
            if (attributeNode instanceof Map<?, ?> attributeMap) {
                attributeMap.forEach((key, value) -> {
                    String attributeId = Texts.normalizeId(Texts.toStringSafe(key));
                    if (Texts.isNotBlank(attributeId) && value instanceof Number number) {
                        attributes.merge(attributeId, number.doubleValue(), Double::sum);
                    }
                });
            }
            Object skillNode = raw.get("es_skills");
            if (skillNode instanceof Iterable<?> skillList) {
                for (Object skill : skillList) {
                    String skillId = Texts.normalizeId(Texts.toStringSafe(skill));
                    if (Texts.isNotBlank(skillId) && !skills.contains(skillId)) {
                        skills.add(skillId);
                    }
                }
            }
        }
    }
}
