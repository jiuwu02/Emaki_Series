package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class QualitySettings {

    public record QualityTier(String name, int weight, double multiplier) {

        public static QualityTier fromString(String raw) {
            if (Texts.isBlank(raw)) {
                return null;
            }
            String[] parts = Texts.toStringSafe(raw).split("-", 3);
            if (parts.length != 3) {
                return null;
            }
            String name = Texts.trim(parts[0]);
            int weight = Numbers.tryParseInt(parts[1], 0);
            double multiplier = Numbers.tryParseDouble(parts[2], 1D);
            if (Texts.isBlank(name) || weight <= 0) {
                return null;
            }
            return new QualityTier(name, weight, multiplier);
        }
    }

    private final List<QualityTier> tiers;
    private final String defaultTier;
    private final boolean guaranteeEnabled;
    private final int guaranteeThreshold;
    private final String guaranteeMinimum;
    private final boolean itemMetaEnabled;
    private final Map<String, List<String>> itemMetaActions;
    private final Map<String, Object> itemMetaNameActions;
    private final Map<String, Object> itemMetaLoreActions;

    public QualitySettings(List<QualityTier> tiers,
            String defaultTier,
            boolean guaranteeEnabled,
            int guaranteeThreshold,
            String guaranteeMinimum,
            boolean itemMetaEnabled,
            Map<String, List<String>> itemMetaActions,
            Map<String, Object> itemMetaNameActions,
            Map<String, Object> itemMetaLoreActions) {
        this.tiers = List.copyOf(tiers);
        this.defaultTier = defaultTier;
        this.guaranteeEnabled = guaranteeEnabled;
        this.guaranteeThreshold = guaranteeThreshold;
        this.guaranteeMinimum = guaranteeMinimum;
        this.itemMetaEnabled = itemMetaEnabled;
        this.itemMetaActions = Map.copyOf(itemMetaActions);
        this.itemMetaNameActions = Map.copyOf(itemMetaNameActions);
        this.itemMetaLoreActions = Map.copyOf(itemMetaLoreActions);
    }

    public static QualitySettings defaults() {
        QualityTier normal = QualityTier.fromString("普通-100-1.0");
        return new QualitySettings(List.of(normal), "普通", false, 10, "普通", false, Map.of(), Map.of(), Map.of());
    }

    public static QualitySettings fromConfig(Object raw) {
        if (raw == null) {
            return defaults();
        }
        List<QualityTier> tiers = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(ConfigNodes.get(raw, "tiers"))) {
            QualityTier tier = QualityTier.fromString(Texts.toStringSafe(entry));
            if (tier != null) {
                tiers.add(tier);
            }
        }
        if (tiers.isEmpty()) {
            return defaults();
        }
        Object guarantee = ConfigNodes.get(raw, "guarantee");
        Object itemMeta = ConfigNodes.get(raw, "item_meta");
        Map<String, List<String>> actions = new LinkedHashMap<>();
        Map<String, Object> nameActions = new LinkedHashMap<>();
        Map<String, Object> loreActions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(ConfigNodes.get(itemMeta, "tiers")).entrySet()) {
            String key = Texts.lower(entry.getKey());
            Object tierConfig = entry.getValue();
            actions.put(key, List.copyOf(Texts.asStringList(ConfigNodes.get(tierConfig, "action"))));
            Object nameActionsRaw = ConfigNodes.get(tierConfig, "name_actions");
            if (nameActionsRaw != null) {
                nameActions.put(key, nameActionsRaw);
            }
            Object loreActionsRaw = ConfigNodes.get(tierConfig, "lore_actions");
            if (loreActionsRaw != null) {
                loreActions.put(key, loreActionsRaw);
            }
        }
        return new QualitySettings(
                tiers,
                ConfigNodes.string(raw, "default_tier", "普通"),
                ConfigNodes.bool(guarantee, "enabled", false),
                Numbers.tryParseInt(ConfigNodes.get(guarantee, "threshold"), 10),
                ConfigNodes.string(guarantee, "minimum", "普通"),
                ConfigNodes.bool(itemMeta, "enabled", false),
                actions,
                nameActions,
                loreActions
        );
    }

    public QualityTier defaultTier() {
        QualityTier tier = findTier(defaultTier);
        return tier == null ? tiers.get(0) : tier;
    }

    public QualityTier minimumTier() {
        QualityTier tier = findTier(guaranteeMinimum);
        return tier == null ? defaultTier() : tier;
    }

    public QualityTier findTier(String name) {
        if (Texts.isBlank(name)) {
            return null;
        }
        for (QualityTier tier : tiers) {
            if (Texts.lower(tier.name()).equals(Texts.lower(name))) {
                return tier;
            }
        }
        return null;
    }

    public QualityTier tierByName(String name) {
        QualityTier tier = findTier(name);
        return tier == null ? defaultTier() : tier;
    }

    public int tierIndex(String name) {
        if (Texts.isBlank(name)) {
            return -1;
        }
        for (int index = 0; index < tiers.size(); index++) {
            if (Texts.lower(tiers.get(index).name()).equals(Texts.lower(name))) {
                return index;
            }
        }
        return -1;
    }

    public int tierIndex(QualityTier tier) {
        return tier == null ? -1 : tierIndex(tier.name());
    }

    public boolean hasTier(String name) {
        return tierIndex(name) >= 0;
    }

    public QualityTier higherTier(QualityTier first, QualityTier second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return tierIndex(second) > tierIndex(first) ? second : first;
    }

    public Object itemMetaNameActions(String tierName) {
        return itemMetaNameActions.get(Texts.lower(tierName));
    }

    public Object itemMetaLoreActions(String tierName) {
        return itemMetaLoreActions.get(Texts.lower(tierName));
    }

    public List<String> itemMetaActions(String tierName) {
        return itemMetaActions.getOrDefault(Texts.lower(tierName), List.of());
    }

    public List<QualityTier> tiers() {
        return tiers;
    }

    public boolean guaranteeEnabled() {
        return guaranteeEnabled;
    }

    public int guaranteeThreshold() {
        return guaranteeThreshold;
    }

    public boolean itemMetaEnabled() {
        return itemMetaEnabled;
    }
}
