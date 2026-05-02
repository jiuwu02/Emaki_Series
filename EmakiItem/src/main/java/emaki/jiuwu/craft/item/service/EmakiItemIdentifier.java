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

    void writeIdentity(ItemMeta itemMeta, String id) {
        if (itemMeta == null || Texts.isBlank(id)) {
            return;
        }
        pdcService.set(itemMeta, partition, FIELD_ID, PersistentDataType.STRING, Texts.normalizeId(id));
        pdcService.set(itemMeta, partition, FIELD_SCHEMA_VERSION, PersistentDataType.INTEGER, SCHEMA_VERSION);
    }
}
