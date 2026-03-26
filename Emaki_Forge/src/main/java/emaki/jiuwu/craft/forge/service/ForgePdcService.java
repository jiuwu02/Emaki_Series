package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ForgePdcService {

    private static final String NAMESPACE = "emaki_forge";
    private static final String LEGACY_NAMESPACE = "jiuwu_forge";
    private static final int SCHEMA_VERSION = 2;

    public record MaterialRecord(String category,
                                 String materialId,
                                 int amount,
                                 int slot,
                                 int sequence,
                                 ItemSource source,
                                 long timestamp) {

        public MaterialRecord {
            category = Texts.isBlank(category) ? "unknown" : category;
            materialId = Texts.toStringSafe(materialId);
            timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        }
    }

    private record ForgeSnapshot(int schemaVersion,
                                 String recipeId,
                                 String quality,
                                 double multiplier,
                                 long forgedAt,
                                 List<MaterialRecord> materials,
                                 Map<String, Integer> aggregatedMaterials,
                                 boolean corrupted,
                                 String corruptionReason) {
    }

    private final EmakiForgePlugin plugin;
    private final Object mutex = new Object();

    private final NamespacedKey schemaVersionKey;
    private final NamespacedKey recipeIdKey;
    private final NamespacedKey qualityKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey forgedAtKey;
    private final NamespacedKey materialsBlobKey;
    private final NamespacedKey corruptedKey;
    private final NamespacedKey corruptionReasonKey;

    private final List<NamespacedKey> schemaVersionReadKeys;
    private final List<NamespacedKey> recipeIdReadKeys;
    private final List<NamespacedKey> qualityReadKeys;
    private final List<NamespacedKey> multiplierReadKeys;
    private final List<NamespacedKey> forgedAtReadKeys;
    private final List<NamespacedKey> materialsBlobReadKeys;
    private final List<NamespacedKey> corruptedReadKeys;
    private final List<NamespacedKey> corruptionReasonReadKeys;
    private final List<NamespacedKey> allManagedKeys;

    public ForgePdcService(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.schemaVersionKey = key("forge_schema_version");
        this.recipeIdKey = key("forge_recipe_id");
        this.qualityKey = key("forge_quality");
        this.multiplierKey = key("forge_multiplier");
        this.forgedAtKey = key("forge_forged_at");
        this.materialsBlobKey = key("forge_materials");
        this.corruptedKey = key("forge_data_corrupted");
        this.corruptionReasonKey = key("forge_corruption_reason");

        this.schemaVersionReadKeys = readKeys(plugin, schemaVersionKey, "forge_schema_version");
        this.recipeIdReadKeys = readKeys(plugin, recipeIdKey, "forge_recipe_id");
        this.qualityReadKeys = readKeys(plugin, qualityKey, "forge_quality");
        this.multiplierReadKeys = readKeys(plugin, multiplierKey, "forge_multiplier");
        this.forgedAtReadKeys = readKeys(plugin, forgedAtKey, "forge_forged_at");
        this.materialsBlobReadKeys = readKeys(plugin, materialsBlobKey, "forge_materials");
        this.corruptedReadKeys = readKeys(plugin, corruptedKey, "forge_data_corrupted");
        this.corruptionReasonReadKeys = readKeys(plugin, corruptionReasonKey, "forge_corruption_reason");
        this.allManagedKeys = List.of(
            schemaVersionKey, recipeIdKey, qualityKey, multiplierKey, forgedAtKey,
            materialsBlobKey, corruptedKey, corruptionReasonKey,
            legacyNamespaceKey("forge_schema_version"),
            legacyNamespaceKey("forge_recipe_id"),
            legacyNamespaceKey("forge_quality"),
            legacyNamespaceKey("forge_multiplier"),
            legacyNamespaceKey("forge_forged_at"),
            legacyNamespaceKey("forge_materials"),
            legacyNamespaceKey("forge_data_corrupted"),
            legacyNamespaceKey("forge_corruption_reason"),
            new NamespacedKey(plugin, "forge_schema_version"),
            new NamespacedKey(plugin, "forge_recipe_id"),
            new NamespacedKey(plugin, "forge_quality"),
            new NamespacedKey(plugin, "forge_multiplier"),
            new NamespacedKey(plugin, "forge_forged_at"),
            new NamespacedKey(plugin, "forge_materials"),
            new NamespacedKey(plugin, "forge_data_corrupted"),
            new NamespacedKey(plugin, "forge_corruption_reason")
        );
    }

    public boolean apply(ItemStack itemStack,
                         Recipe recipe,
                         List<MaterialRecord> materials,
                         QualitySettings.QualityTier qualityTier,
                         double multiplier) {
        synchronized (mutex) {
            if (!Bukkit.isPrimaryThread()) {
                plugin.getLogger().warning("Skipped forge PDC write outside the primary thread.");
                return false;
            }
            if (itemStack == null || recipe == null) {
                return false;
            }
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta == null) {
                return false;
            }
            try {
                long forgedAt = System.currentTimeMillis();
                List<MaterialRecord> normalized = normalizeMaterials(materials, forgedAt);
                PersistentDataContainer container = itemMeta.getPersistentDataContainer();
                container.set(schemaVersionKey, PersistentDataType.INTEGER, SCHEMA_VERSION);
                container.set(recipeIdKey, PersistentDataType.STRING, recipe.id());
                container.set(forgedAtKey, PersistentDataType.LONG, forgedAt);
                container.set(multiplierKey, PersistentDataType.DOUBLE, multiplier);
                if (qualityTier != null) {
                    container.set(qualityKey, PersistentDataType.STRING, qualityTier.name());
                } else {
                    container.remove(qualityKey);
                }
                container.set(materialsBlobKey, PersistentDataType.STRING, serializeMaterials(normalized));
                clearCorruptedState(container);
                clearLegacyKeys(container);
                itemStack.setItemMeta(itemMeta);
                return true;
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to write forge PDC data for recipe " + recipe.id() + ": " + exception.getMessage());
                return false;
            }
        }
    }

    public List<MaterialRecord> getMaterialsFromItem(ItemStack itemStack) {
        synchronized (mutex) {
            ForgeSnapshot snapshot = readSnapshot(itemStack, true);
            return snapshot == null ? List.of() : snapshot.materials();
        }
    }

    public boolean hasForgeData(ItemStack itemStack) {
        synchronized (mutex) {
            PersistentDataContainer container = container(itemStack);
            if (container == null) {
                return false;
            }
            return readValue(container, recipeIdReadKeys, PersistentDataType.STRING) != null
                || readValue(container, materialsBlobReadKeys, PersistentDataType.STRING) != null
                || readValue(container, schemaVersionReadKeys, PersistentDataType.INTEGER) != null;
        }
    }

    public int getMaterialCount(ItemStack itemStack, String materialId) {
        synchronized (mutex) {
            if (Texts.isBlank(materialId)) {
                return 0;
            }
            ForgeSnapshot snapshot = readSnapshot(itemStack, true);
            return snapshot == null ? 0 : snapshot.aggregatedMaterials().getOrDefault(materialId, 0);
        }
    }

    public Long getForgeTimestamp(ItemStack itemStack) {
        synchronized (mutex) {
            ForgeSnapshot snapshot = readSnapshot(itemStack, true);
            return snapshot == null || snapshot.forgedAt() <= 0 ? null : snapshot.forgedAt();
        }
    }

    public Map<String, Integer> getAggregatedMaterialCounts(ItemStack itemStack) {
        synchronized (mutex) {
            ForgeSnapshot snapshot = readSnapshot(itemStack, true);
            return snapshot == null ? Map.of() : Map.copyOf(snapshot.aggregatedMaterials());
        }
    }

    public Map<String, Integer> getDecomposeMaterialCounts(ItemStack itemStack) {
        return getAggregatedMaterialCounts(itemStack);
    }

    public boolean isCorrupted(ItemStack itemStack) {
        synchronized (mutex) {
            ForgeSnapshot snapshot = readSnapshot(itemStack, false);
            return snapshot != null && snapshot.corrupted();
        }
    }

    public String getCorruptionReason(ItemStack itemStack) {
        synchronized (mutex) {
            ForgeSnapshot snapshot = readSnapshot(itemStack, false);
            return snapshot == null ? null : snapshot.corruptionReason();
        }
    }

    public void clearForgeData(ItemStack itemStack) {
        synchronized (mutex) {
            if (itemStack == null || !Bukkit.isPrimaryThread()) {
                return;
            }
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta == null) {
                return;
            }
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            for (NamespacedKey key : allManagedKeys) {
                container.remove(key);
            }
            itemStack.setItemMeta(itemMeta);
        }
    }

    private ForgeSnapshot readSnapshot(ItemStack itemStack, boolean markCorrupted) {
        PersistentDataContainer container = container(itemStack);
        if (container == null) {
            return null;
        }
        if (!hasForgeData(itemStack) && readBooleanFlag(container, corruptedReadKeys) == null) {
            return null;
        }
        Integer schemaVersion = readValue(container, schemaVersionReadKeys, PersistentDataType.INTEGER);
        String recipeId = readValue(container, recipeIdReadKeys, PersistentDataType.STRING);
        String quality = readValue(container, qualityReadKeys, PersistentDataType.STRING);
        Double multiplier = readValue(container, multiplierReadKeys, PersistentDataType.DOUBLE);
        Long forgedAt = readForgedAt(container);
        String blob = readValue(container, materialsBlobReadKeys, PersistentDataType.STRING);
        Byte corruptedFlag = readBooleanFlag(container, corruptedReadKeys);
        String corruptionReason = readValue(container, corruptionReasonReadKeys, PersistentDataType.STRING);

        List<MaterialRecord> materials = deserializeMaterials(blob);
        String validationError = validateSnapshot(recipeId, forgedAt, blob, materials, corruptedFlag != null && corruptedFlag == (byte) 1, corruptionReason);
        if (validationError != null) {
            plugin.getLogger().warning("Corrupted forge PDC data detected: " + validationError);
            if (markCorrupted) {
                markCorrupted(itemStack, validationError);
            }
            return new ForgeSnapshot(
                schemaVersion == null ? 0 : schemaVersion,
                recipeId,
                quality,
                multiplier == null ? 1D : multiplier,
                forgedAt == null ? 0L : forgedAt,
                List.of(),
                Map.of(),
                true,
                validationError
            );
        }
        return new ForgeSnapshot(
            schemaVersion == null ? 1 : schemaVersion,
            recipeId,
            quality,
            multiplier == null ? 1D : multiplier,
            forgedAt == null ? 0L : forgedAt,
            materials,
            aggregateMaterials(materials),
                corruptedFlag != null && corruptedFlag == (byte) 1,
                corruptionReason
            );
        }

    private String validateSnapshot(String recipeId,
                                    Long forgedAt,
                                    String materialsBlob,
                                    List<MaterialRecord> materials,
                                    boolean corrupted,
                                    String corruptionReason) {
        if (corrupted && Texts.isNotBlank(corruptionReason)) {
            return corruptionReason;
        }
        if (Texts.isBlank(recipeId)) {
            return "Missing forge_recipe_id";
        }
        if (forgedAt == null || forgedAt <= 0) {
            return "Missing forge_forged_at";
        }
        if (Texts.isBlank(materialsBlob)) {
            return "Missing forge_materials";
        }
        if (materials == null) {
            return "Invalid forge_materials payload";
        }
        for (MaterialRecord material : materials) {
            if (material == null) {
                return "Null material record";
            }
            if (Texts.isBlank(material.materialId())) {
                return "Material record missing material_id";
            }
            if (material.amount() <= 0) {
                return "Material record has invalid amount for " + material.materialId();
            }
            if (material.timestamp() <= 0) {
                return "Material record missing timestamp for " + material.materialId();
            }
        }
        return null;
    }

    private List<MaterialRecord> normalizeMaterials(List<MaterialRecord> materials, long fallbackTimestamp) {
        List<MaterialRecord> result = new ArrayList<>();
        if (materials == null) {
            return result;
        }
        for (MaterialRecord material : materials) {
            if (material == null || Texts.isBlank(material.materialId()) || material.amount() <= 0) {
                continue;
            }
            result.add(new MaterialRecord(
                material.category(),
                material.materialId(),
                material.amount(),
                material.slot(),
                material.sequence(),
                material.source(),
                material.timestamp() > 0 ? material.timestamp() : fallbackTimestamp
            ));
        }
        return result;
    }

    private String serializeMaterials(List<MaterialRecord> materials) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema_version", SCHEMA_VERSION);
        int index = 0;
        for (MaterialRecord material : materials) {
            String path = "materials." + index++;
            yaml.set(path + ".category", material.category());
            yaml.set(path + ".material_id", material.materialId());
            yaml.set(path + ".amount", material.amount());
            yaml.set(path + ".slot", material.slot());
            yaml.set(path + ".sequence", material.sequence());
            yaml.set(path + ".timestamp", material.timestamp());
            if (material.source() != null) {
                yaml.set(path + ".source.type", material.source().getType().name().toLowerCase());
                yaml.set(path + ".source.identifier", material.source().getIdentifier());
            }
        }
        for (Map.Entry<String, Integer> entry : aggregateMaterials(materials).entrySet()) {
            yaml.set("aggregated_materials." + entry.getKey() + ".amount", entry.getValue());
            yaml.set("aggregated_materials." + entry.getKey() + ".timestamp", latestTimestamp(materials, entry.getKey()));
        }
        return yaml.saveToString();
    }

    private List<MaterialRecord> deserializeMaterials(String blob) {
        if (Texts.isBlank(blob)) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new StringReader(blob));
        } catch (InvalidConfigurationException exception) {
            plugin.getLogger().warning("Failed to deserialize forge materials blob: " + exception.getMessage());
            return null;
        } catch (Exception exception) {
            plugin.getLogger().warning("Unexpected forge materials blob error: " + exception.getMessage());
            return null;
        }
        ConfigurationSection materialsSection = yaml.getConfigurationSection("materials");
        if (materialsSection == null) {
            return null;
        }
        List<MaterialRecord> result = new ArrayList<>();
        for (String key : materialsSection.getKeys(false)) {
            ConfigurationSection entry = materialsSection.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            result.add(new MaterialRecord(
                entry.getString("category", "unknown"),
                entry.getString("material_id"),
                entry.getInt("amount", 0),
                entry.getInt("slot", -1),
                entry.getInt("sequence", result.size()),
                ItemSourceUtil.parse(entry.getConfigurationSection("source")),
                entry.getLong("timestamp", 0L)
            ));
        }
        return result;
    }

    private Map<String, Integer> aggregateMaterials(List<MaterialRecord> materials) {
        Map<String, Integer> aggregated = new LinkedHashMap<>();
        for (MaterialRecord material : materials) {
            aggregated.merge(material.materialId(), material.amount(), Integer::sum);
        }
        return aggregated;
    }

    private long latestTimestamp(List<MaterialRecord> materials, String materialId) {
        long latest = 0L;
        for (MaterialRecord material : materials) {
            if (Objects.equals(material.materialId(), materialId)) {
                latest = Math.max(latest, material.timestamp());
            }
        }
        return latest;
    }

    private void markCorrupted(ItemStack itemStack, String reason) {
        if (itemStack == null || Texts.isBlank(reason) || !Bukkit.isPrimaryThread()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        container.set(corruptedKey, PersistentDataType.BYTE, (byte) 1);
        container.set(corruptionReasonKey, PersistentDataType.STRING, reason);
        itemStack.setItemMeta(itemMeta);
    }

    private void clearCorruptedState(PersistentDataContainer container) {
        container.remove(corruptedKey);
        container.remove(corruptionReasonKey);
        container.remove(legacyNamespaceKey("forge_data_corrupted"));
        container.remove(legacyNamespaceKey("forge_corruption_reason"));
        container.remove(new NamespacedKey(plugin, "forge_data_corrupted"));
        container.remove(new NamespacedKey(plugin, "forge_corruption_reason"));
    }

    private void clearLegacyKeys(PersistentDataContainer container) {
        container.remove(legacyNamespaceKey("forge_schema_version"));
        container.remove(legacyNamespaceKey("forge_recipe_id"));
        container.remove(legacyNamespaceKey("forge_quality"));
        container.remove(legacyNamespaceKey("forge_multiplier"));
        container.remove(legacyNamespaceKey("forge_forged_at"));
        container.remove(legacyNamespaceKey("forge_materials"));
        container.remove(new NamespacedKey(plugin, "forge_schema_version"));
        container.remove(new NamespacedKey(plugin, "forge_recipe_id"));
        container.remove(new NamespacedKey(plugin, "forge_quality"));
        container.remove(new NamespacedKey(plugin, "forge_multiplier"));
        container.remove(new NamespacedKey(plugin, "forge_forged_at"));
        container.remove(new NamespacedKey(plugin, "forge_materials"));
    }

    private PersistentDataContainer container(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta == null ? null : itemMeta.getPersistentDataContainer();
    }

    private Long readForgedAt(PersistentDataContainer container) {
        Long value = readValue(container, forgedAtReadKeys, PersistentDataType.LONG);
        if (value != null && value > 0) {
            return value;
        }
        String legacy = readValue(container, forgedAtReadKeys, PersistentDataType.STRING);
        if (Texts.isBlank(legacy)) {
            return null;
        }
        try {
            return Instant.parse(legacy).toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Byte readBooleanFlag(PersistentDataContainer container, List<NamespacedKey> keys) {
        return readValue(container, keys, PersistentDataType.BYTE);
    }

    private <P, C> C readValue(PersistentDataContainer container,
                               List<NamespacedKey> keys,
                               PersistentDataType<P, C> type) {
        for (NamespacedKey key : keys) {
            if (container.has(key, type)) {
                return container.get(key, type);
            }
        }
        return null;
    }

    private static NamespacedKey key(String path) {
        return Objects.requireNonNull(NamespacedKey.fromString(NAMESPACE + ":" + path));
    }

    private static List<NamespacedKey> readKeys(EmakiForgePlugin plugin, NamespacedKey primary, String legacyPath) {
        return List.of(primary, legacyNamespaceKey(legacyPath), new NamespacedKey(plugin, legacyPath));
    }

    private static NamespacedKey legacyNamespaceKey(String path) {
        return Objects.requireNonNull(NamespacedKey.fromString(LEGACY_NAMESPACE + ":" + path));
    }
}
