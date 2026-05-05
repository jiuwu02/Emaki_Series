package emaki.jiuwu.craft.item.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EmakiItemIdentifier {

    static final String PARTITION = "emakiitem";
    static final String FIELD_ID = "id";
    static final String FIELD_SCHEMA_VERSION = "schema_version";
    static final String FIELD_DEFINITION_SIGNATURE = "definition_signature";
    static final String FIELD_UPDATED_AT = "updated_at";
    static final String FIELD_SET_ID = "set_id";
    static final String FIELD_SET_PIECE = "set_piece";
    static final String FIELD_SET_ACTIVE_COUNT = "set_active_count";
    static final String FIELD_SET_TOTAL_COUNT = "set_total_count";
    static final String FIELD_SET_ACTIVE_THRESHOLDS = "set_active_thresholds";
    static final String FIELD_SET_SIGNATURE = "set_signature";
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

    void writeIdentity(ItemMeta itemMeta, String id, String definitionSignature) {
        if (itemMeta == null || Texts.isBlank(id)) {
            return;
        }
        pdcService.set(itemMeta, partition, FIELD_ID, PersistentDataType.STRING, Texts.normalizeId(id));
        pdcService.set(itemMeta, partition, FIELD_SCHEMA_VERSION, PersistentDataType.INTEGER, SCHEMA_VERSION);
        pdcService.set(itemMeta, partition, FIELD_DEFINITION_SIGNATURE, PersistentDataType.STRING, definitionSignature == null ? "" : definitionSignature);
        pdcService.set(itemMeta, partition, FIELD_UPDATED_AT, PersistentDataType.LONG, System.currentTimeMillis());
    }

    void writeSetState(ItemMeta itemMeta,
            String setId,
            String setPiece,
            int activeCount,
            int totalCount,
            String activeThresholds,
            String setSignature) {
        if (itemMeta == null) {
            return;
        }
        pdcService.set(itemMeta, partition, FIELD_SET_ID, PersistentDataType.STRING, Texts.normalizeId(setId));
        pdcService.set(itemMeta, partition, FIELD_SET_PIECE, PersistentDataType.STRING, Texts.normalizeId(setPiece));
        pdcService.set(itemMeta, partition, FIELD_SET_ACTIVE_COUNT, PersistentDataType.INTEGER, Math.max(0, activeCount));
        pdcService.set(itemMeta, partition, FIELD_SET_TOTAL_COUNT, PersistentDataType.INTEGER, Math.max(0, totalCount));
        pdcService.set(itemMeta, partition, FIELD_SET_ACTIVE_THRESHOLDS, PersistentDataType.STRING, activeThresholds == null ? "" : activeThresholds);
        pdcService.set(itemMeta, partition, FIELD_SET_SIGNATURE, PersistentDataType.STRING, setSignature == null ? "" : setSignature);
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
    }
}
