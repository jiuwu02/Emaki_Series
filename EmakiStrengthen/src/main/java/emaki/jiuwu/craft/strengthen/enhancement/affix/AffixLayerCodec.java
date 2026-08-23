package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;

public final class AffixLayerCodec {

    private static final String PARTITION_PATH = "strengthen_affix";

    private static final String LEGACY_PARTITION_PATH = "strengthen.affix";
    private static final String FIELD = "layer";
    private static final int SCHEMA_VERSION = 1;

    private final PdcService pdcService;
    private final PdcPartition partition;
    private final SnapshotCodec<AffixLayer> codec;

    public AffixLayerCodec(@NotNull PdcService pdcService) {
        this.pdcService = pdcService;
        this.partition = pdcService.partition(PARTITION_PATH);
        this.codec = SnapshotCodec.yaml(AffixLayerCodec::encode, AffixLayerCodec::decode);
    }

    public static @NotNull String partitionPath() {
        return PARTITION_PATH;
    }

    public static @NotNull String legacyPartitionPath() {
        return LEGACY_PARTITION_PATH;
    }

    public @Nullable AffixLayer read(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return pdcService.readBlobMigrating(itemStack, partition, LEGACY_PARTITION_PATH, FIELD, codec);
    }

    public @NotNull AffixLayer readOrEmpty(@Nullable ItemStack itemStack, int defaultCapacityMax) {
        AffixLayer stored = read(itemStack);
        return stored == null ? AffixLayer.empty(defaultCapacityMax) : stored;
    }

    public boolean write(@Nullable ItemStack itemStack, @Nullable AffixLayer layer) {
        if (itemStack == null || itemStack.getType().isAir() || layer == null) {
            return false;
        }
        boolean written = pdcService.writeBlob(itemStack, partition, FIELD, codec, layer);
        if (written) {

            pdcService.purgeLegacyKeys(itemStack);
        }
        return written;
    }

    public void clear(@Nullable ItemStack itemStack) {
        if (itemStack != null && !itemStack.getType().isAir()) {
            pdcService.removeMigrating(itemStack, partition, LEGACY_PARTITION_PATH, FIELD);
        }
    }

    private static Map<String, Object> encode(AffixLayer layer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(SnapshotCodec.SCHEMA_VERSION_FIELD, SCHEMA_VERSION);
        data.put("affix_capacity_max", layer.capacityMax());
        data.put("affix_capacity_used", layer.capacityUsed());
        List<Map<String, Object>> affixes = new ArrayList<>();
        for (AffixState state : layer.affixes().values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", state.attributeKey());
            entry.put("level", state.level());
            entry.put("bonus", state.bonus());
            entry.put("capacity_cost", state.capacityCost());
            affixes.add(entry);
        }
        data.put("affixes", affixes);
        return data;
    }

    private static AffixLayer decode(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        int capacityMax = Numbers.tryParseInt(data.get("affix_capacity_max"), 0);
        Map<String, AffixState> affixes = new LinkedHashMap<>();
        if (data.get("affixes") instanceof Iterable<?> iterable) {
            for (Object raw : iterable) {
                if (!(raw instanceof Map<?, ?> entry)) {
                    continue;
                }
                String key = Texts.lower(entry.get("key"));
                if (Texts.isBlank(key)) {
                    continue;
                }
                affixes.put(key, new AffixState(
                        key,
                        Numbers.tryParseInt(entry.get("level"), 0),
                        Numbers.tryParseDouble(entry.get("bonus"), 0D),
                        Numbers.tryParseInt(entry.get("capacity_cost"), 0)
                ));
            }
        }
        return new AffixLayer(capacityMax, affixes);
    }
}
