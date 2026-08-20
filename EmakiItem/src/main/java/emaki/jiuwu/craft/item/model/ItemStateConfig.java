package emaki.jiuwu.craft.item.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.ItemStateSchema;
import emaki.jiuwu.craft.item.api.ItemStateType;

public record ItemStateConfig(boolean clampEnabled,
        boolean fillDefaults,
        Map<String, Field> fields,
        List<Migration> migrations,
        Preservation preservation,
        Derivation derivation) {

    public static final int MAX_THRESHOLDS_PER_FIELD = 64;

    public record Field(String key,
            ItemStateType type,
            Object defaultValue,
            BigDecimal minimum,
            BigDecimal maximum,
            List<Threshold> thresholds) {

        public Field {
            key = normalizeKey(key);
            thresholds = thresholds == null ? List.of() : List.copyOf(thresholds);
        }

        public boolean bounded() {
            return minimum != null || maximum != null;
        }
    }

    public record Threshold(String id,
            BigDecimal value,
            boolean once,
            boolean rewardOnFall,
            List<String> actions,
            String messageKey,
            String sound,
            float soundVolume,
            float soundPitch,
            boolean refreshDerived) {

        public Threshold {
            id = normalizeKey(id);
            actions = actions == null ? List.of() : List.copyOf(actions);
            messageKey = Texts.toStringSafe(messageKey);
            sound = Texts.toStringSafe(sound);
        }
    }

    public record Migration(int fromVersion,
            int toVersion,
            Map<String, String> renamedFields,
            Map<String, ItemStateType> retypedFields,
            List<String> droppedFields) {

        public Migration {
            renamedFields = renamedFields == null ? Map.of() : Map.copyOf(renamedFields);
            retypedFields = retypedFields == null ? Map.of() : Map.copyOf(retypedFields);
            droppedFields = droppedFields == null ? List.of() : List.copyOf(droppedFields);
        }
    }

    public record Preservation(boolean verifyRebuild,
            boolean repairOnPickup,
            boolean repairOnDrop,
            boolean repairOnTrade,
            boolean repairOnContainerTransfer,
            boolean repairOnJoin) {

        public static Preservation defaults() {
            return new Preservation(true, false, false, false, false, false);
        }
    }

    public record Derivation(boolean enabled,
            boolean refreshLore,
            boolean refreshAttributes,
            boolean scanHolder,
            int maxDepth) {

        public Derivation {
            maxDepth = Math.max(1, Math.min(8, maxDepth));
        }

        public static Derivation defaults() {
            return new Derivation(false, true, true, true, 2);
        }
    }

    public ItemStateConfig {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        migrations = migrations == null ? List.of() : List.copyOf(migrations);
        preservation = preservation == null ? Preservation.defaults() : preservation;
        derivation = derivation == null ? Derivation.defaults() : derivation;
    }

    public static ItemStateConfig defaults() {
        return new ItemStateConfig(true, true, Map.of(), List.of(),
                Preservation.defaults(), Derivation.defaults());
    }

    public Field field(String key) {
        return fields.get(normalizeKey(key));
    }

    public boolean hasThresholds() {
        return fields.values().stream().anyMatch(field -> !field.thresholds().isEmpty());
    }

    public List<Migration> migrationPath(int storedVersion) {
        List<Migration> path = new ArrayList<>();
        int current = storedVersion;
        while (current < ItemStateSchema.CURRENT_SCHEMA_VERSION) {
            Migration step = migrationFrom(current);
            if (step == null) {
                return path;
            }
            path.add(step);
            current = step.toVersion();
        }
        return path;
    }

    private Migration migrationFrom(int version) {
        for (Migration migration : migrations) {
            if (migration.fromVersion() == version && migration.toVersion() > version) {
                return migration;
            }
        }
        return null;
    }

    static String normalizeKey(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
    }
}
