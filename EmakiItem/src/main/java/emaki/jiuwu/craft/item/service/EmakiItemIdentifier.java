package emaki.jiuwu.craft.item.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EmakiItemIdentifier {

    static final String PARTITION = "emakiitem";
    static final String FIELD_ID = "id";
    static final String FIELD_SCHEMA_VERSION = "schema_version";
    static final String FIELD_UPDATE_VERSION = "update_version";
    static final String FIELD_DEFINITION_SIGNATURE = "definition_signature";
    static final String FIELD_UPDATED_AT = "updated_at";
    static final String FIELD_SET_ID = "set_id";
    static final String FIELD_SET_PIECE = "set_piece";
    static final String FIELD_SET_ACTIVE_COUNT = "set_active_count";
    static final String FIELD_SET_TOTAL_COUNT = "set_total_count";
    static final String FIELD_SET_ACTIVE_THRESHOLDS = "set_active_thresholds";
    static final String FIELD_SET_SIGNATURE = "set_signature";
    static final String FIELD_SET_LORE_LINES = "set_lore_lines";
    static final int SCHEMA_VERSION = 1;

    private final PdcService pdcService;
    private final PdcPartition partition;

    public EmakiItemIdentifier(PdcService pdcService) {
        this.pdcService = pdcService;
        this.partition = pdcService.partition(PARTITION);
    }

    public String identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        String id = pdcService.get(itemStack, partition, FIELD_ID, PersistentDataType.STRING);
        return Texts.normalizeId(id);
    }

    public Integer schemaVersion(ItemStack itemStack) {
        return pdcService.get(itemStack, partition, FIELD_SCHEMA_VERSION, PersistentDataType.INTEGER);
    }

    public int updateVersion(ItemStack itemStack) {
        Integer version = pdcService.get(itemStack, partition, FIELD_UPDATE_VERSION, PersistentDataType.INTEGER);
        return version == null ? 0 : Math.max(0, version);
    }

    public String definitionSignature(ItemStack itemStack) {
        String signature = pdcService.get(itemStack, partition, FIELD_DEFINITION_SIGNATURE, PersistentDataType.STRING);
        return signature == null ? "" : signature;
    }

    public String setId(ItemStack itemStack) {
        String value = pdcService.get(itemStack, partition, FIELD_SET_ID, PersistentDataType.STRING);
        return Texts.normalizeId(value);
    }

    public String setPiece(ItemStack itemStack) {
        String value = pdcService.get(itemStack, partition, FIELD_SET_PIECE, PersistentDataType.STRING);
        return Texts.normalizeId(value);
    }

    public Integer setActiveCount(ItemStack itemStack) {
        return pdcService.get(itemStack, partition, FIELD_SET_ACTIVE_COUNT, PersistentDataType.INTEGER);
    }

    public Integer setTotalCount(ItemStack itemStack) {
        return pdcService.get(itemStack, partition, FIELD_SET_TOTAL_COUNT, PersistentDataType.INTEGER);
    }

    public String setActiveThresholds(ItemStack itemStack) {
        String value = pdcService.get(itemStack, partition, FIELD_SET_ACTIVE_THRESHOLDS, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public String setSignature(ItemStack itemStack) {
        String value = pdcService.get(itemStack, partition, FIELD_SET_SIGNATURE, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public Integer setLoreLines(ItemStack itemStack) {
        Integer value = pdcService.get(itemStack, partition, FIELD_SET_LORE_LINES, PersistentDataType.INTEGER);
        return value == null ? null : Math.max(0, value);
    }

    public Snapshot snapshot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Snapshot.empty();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return snapshot(itemMeta);
    }

    public Snapshot snapshot(ItemMeta itemMeta) {
        if (itemMeta == null) {
            return Snapshot.empty();
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        Integer updateVersion = read(container, FIELD_UPDATE_VERSION, PersistentDataType.INTEGER);
        Integer setActiveCount = read(container, FIELD_SET_ACTIVE_COUNT, PersistentDataType.INTEGER);
        Integer setTotalCount = read(container, FIELD_SET_TOTAL_COUNT, PersistentDataType.INTEGER);
        Integer setLoreLines = read(container, FIELD_SET_LORE_LINES, PersistentDataType.INTEGER);
        boolean setStatePresent = hasAnySetState(container);
        boolean completeSetState = hasCompleteSetState(container);
        return new Snapshot(
                Texts.normalizeId(read(container, FIELD_ID, PersistentDataType.STRING)),
                normalizeNonNegative(read(container, FIELD_SCHEMA_VERSION, PersistentDataType.INTEGER)),
                updateVersion == null ? 0 : Math.max(0, updateVersion),
                Texts.toStringSafe(read(container, FIELD_DEFINITION_SIGNATURE, PersistentDataType.STRING)),
                Texts.normalizeId(read(container, FIELD_SET_ID, PersistentDataType.STRING)),
                Texts.normalizeId(read(container, FIELD_SET_PIECE, PersistentDataType.STRING)),
                setActiveCount == null ? null : Math.max(0, setActiveCount),
                setTotalCount == null ? null : Math.max(0, setTotalCount),
                Texts.toStringSafe(read(container, FIELD_SET_ACTIVE_THRESHOLDS, PersistentDataType.STRING)),
                Texts.toStringSafe(read(container, FIELD_SET_SIGNATURE, PersistentDataType.STRING)),
                setLoreLines == null ? null : Math.max(0, setLoreLines),
                setStatePresent,
                completeSetState
        );
    }

    private boolean hasAnySetState(PersistentDataContainer container) {
        if (container == null) {
            return false;
        }
        return container.getKeys().contains(partition.key(FIELD_SET_ID))
                || container.getKeys().contains(partition.key(FIELD_SET_PIECE))
                || container.getKeys().contains(partition.key(FIELD_SET_ACTIVE_COUNT))
                || container.getKeys().contains(partition.key(FIELD_SET_TOTAL_COUNT))
                || container.getKeys().contains(partition.key(FIELD_SET_ACTIVE_THRESHOLDS))
                || container.getKeys().contains(partition.key(FIELD_SET_SIGNATURE))
                || container.getKeys().contains(partition.key(FIELD_SET_LORE_LINES));
    }

    private boolean hasCompleteSetState(PersistentDataContainer container) {
        return container != null
                && container.has(partition.key(FIELD_SET_ID), PersistentDataType.STRING)
                && container.has(partition.key(FIELD_SET_PIECE), PersistentDataType.STRING)
                && container.has(partition.key(FIELD_SET_ACTIVE_COUNT), PersistentDataType.INTEGER)
                && container.has(partition.key(FIELD_SET_TOTAL_COUNT), PersistentDataType.INTEGER)
                && container.has(partition.key(FIELD_SET_ACTIVE_THRESHOLDS), PersistentDataType.STRING)
                && container.has(partition.key(FIELD_SET_SIGNATURE), PersistentDataType.STRING)
                && !container.getKeys().contains(partition.key(FIELD_SET_LORE_LINES));
    }

    private <P, C> C read(PersistentDataContainer container, String field, PersistentDataType<P, C> type) {
        if (container == null || type == null) {
            return null;
        }
        var key = partition.key(field);
        return container.has(key, type) ? container.get(key, type) : null;
    }

    private Integer normalizeNonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    void writeIdentity(ItemMeta itemMeta, String id, String definitionSignature, Integer updateVersion) {
        if (itemMeta == null || Texts.isBlank(id)) {
            return;
        }
        pdcService.set(itemMeta, partition, FIELD_ID, PersistentDataType.STRING, Texts.normalizeId(id));
        pdcService.set(itemMeta, partition, FIELD_SCHEMA_VERSION, PersistentDataType.INTEGER, SCHEMA_VERSION);
        if (updateVersion != null && updateVersion > 0) {
            pdcService.set(itemMeta, partition, FIELD_UPDATE_VERSION, PersistentDataType.INTEGER, updateVersion);
        } else {
            pdcService.remove(itemMeta, partition, FIELD_UPDATE_VERSION);
        }
        pdcService.set(itemMeta, partition, FIELD_DEFINITION_SIGNATURE, PersistentDataType.STRING, definitionSignature == null ? "" : definitionSignature);
        pdcService.set(itemMeta, partition, FIELD_UPDATED_AT, PersistentDataType.LONG, System.currentTimeMillis());
    }

    void writeSetState(ItemMeta itemMeta,
            String setId,
            String setPiece,
            int activeCount,
            int totalCount,
            String activeThresholds,
            int setLoreLines,
            String setSignature) {
        if (itemMeta == null) {
            return;
        }
        pdcService.set(itemMeta, partition, FIELD_SET_ID, PersistentDataType.STRING, Texts.normalizeId(setId));
        pdcService.set(itemMeta, partition, FIELD_SET_PIECE, PersistentDataType.STRING, Texts.normalizeId(setPiece));
        pdcService.set(itemMeta, partition, FIELD_SET_ACTIVE_COUNT, PersistentDataType.INTEGER, Math.max(0, activeCount));
        pdcService.set(itemMeta, partition, FIELD_SET_TOTAL_COUNT, PersistentDataType.INTEGER, Math.max(0, totalCount));
        pdcService.set(itemMeta, partition, FIELD_SET_ACTIVE_THRESHOLDS, PersistentDataType.STRING, activeThresholds == null ? "" : activeThresholds);
        pdcService.remove(itemMeta, partition, FIELD_SET_LORE_LINES);
        pdcService.set(itemMeta, partition, FIELD_SET_SIGNATURE, PersistentDataType.STRING, setSignature == null ? "" : setSignature);
    }

    void clearSetLoreLines(ItemMeta itemMeta) {
        if (itemMeta != null) {
            pdcService.remove(itemMeta, partition, FIELD_SET_LORE_LINES);
        }
    }

    void clearSetState(ItemMeta itemMeta) {
        if (itemMeta == null) {
            return;
        }
        pdcService.remove(itemMeta, partition, FIELD_SET_ID);
        pdcService.remove(itemMeta, partition, FIELD_SET_PIECE);
        pdcService.remove(itemMeta, partition, FIELD_SET_ACTIVE_COUNT);
        pdcService.remove(itemMeta, partition, FIELD_SET_TOTAL_COUNT);
        pdcService.remove(itemMeta, partition, FIELD_SET_ACTIVE_THRESHOLDS);
        pdcService.remove(itemMeta, partition, FIELD_SET_SIGNATURE);
        pdcService.remove(itemMeta, partition, FIELD_SET_LORE_LINES);
    }

    public record Snapshot(
            String id,
            Integer schemaVersion,
            int updateVersion,
            String definitionSignature,
            String setId,
            String setPiece,
            Integer setActiveCount,
            Integer setTotalCount,
            String setActiveThresholds,
            String setSignature,
            Integer setLoreLines,
            boolean setStatePresent,
            boolean completeSetState) {

        public Snapshot {
            id = Texts.normalizeId(id);
            updateVersion = Math.max(0, updateVersion);
            definitionSignature = Texts.toStringSafe(definitionSignature);
            setId = Texts.normalizeId(setId);
            setPiece = Texts.normalizeId(setPiece);
            setActiveThresholds = Texts.toStringSafe(setActiveThresholds);
            setSignature = Texts.toStringSafe(setSignature);
        }

        public static Snapshot empty() {
            return new Snapshot("", null, 0, "", "", "", null, null, "", "", null, false, false);
        }
    }
}
