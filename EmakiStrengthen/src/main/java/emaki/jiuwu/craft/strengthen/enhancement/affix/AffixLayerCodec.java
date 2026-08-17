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

/**
 * 词条强化层的 PDC 读写。
 *
 * <p>整层以一个 YAML blob 落在 Strengthen 自己的 {@code strengthen.affix} 分区下，与整件星级强化
 * 的状态、以及 Forge 的锻造容量互不干扰。
 */
public final class AffixLayerCodec {

    /** 分区路径。与 Forge 的锻造容量刻意分离，见 ES-02。 */
    private static final String PARTITION_PATH = "strengthen.affix";
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

    /** {@return 物品上的词条层；没有写过时返回 {@code null}} */
    public @Nullable AffixLayer read(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return pdcService.readBlob(itemStack, partition, FIELD, codec);
    }

    /**
     * {@return 物品上的词条层，缺失时以 {@code defaultCapacityMax} 生成一个空层}
     *
     * @param itemStack          目标物品
     * @param defaultCapacityMax 缺省最大容量
     */
    public @NotNull AffixLayer readOrEmpty(@Nullable ItemStack itemStack, int defaultCapacityMax) {
        AffixLayer stored = read(itemStack);
        return stored == null ? AffixLayer.empty(defaultCapacityMax) : stored;
    }

    /** 就地写回词条层。 */
    public boolean write(@Nullable ItemStack itemStack, @Nullable AffixLayer layer) {
        if (itemStack == null || itemStack.getType().isAir() || layer == null) {
            return false;
        }
        return pdcService.writeBlob(itemStack, partition, FIELD, codec, layer);
    }

    /** 移除物品上的词条层。 */
    public void clear(@Nullable ItemStack itemStack) {
        if (itemStack != null && !itemStack.getType().isAir()) {
            pdcService.remove(itemStack, partition, FIELD);
        }
    }

    private static Map<String, Object> encode(AffixLayer layer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(SnapshotCodec.SCHEMA_VERSION_FIELD, SCHEMA_VERSION);
        data.put("affix_capacity_max", layer.capacityMax());
        // 已用容量是派生值，落盘只为便于外部（如 PAPI / 调试）直接读取，恢复时不作为真相来源。
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
        // affix_capacity_used 刻意不回读：由 AffixLayer.capacityUsed() 从明细求和，避免两份真相。
        return new AffixLayer(capacityMax, affixes);
    }
}
