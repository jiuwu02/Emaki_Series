package emaki.jiuwu.craft.strengthen.enhancement.mastery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;

public final class MasteryLayerCodec {

    private static final String PARTITION_PATH = "strengthen.mastery";
    private static final String FIELD = "layer";
    private static final int SCHEMA_VERSION = 1;

    private final PdcService pdcService;
    private final PdcPartition partition;
    private final SnapshotCodec<MasteryLayer> codec;

    public MasteryLayerCodec(@NotNull PdcService pdcService) {
        this.pdcService = pdcService;
        this.partition = pdcService.partition(PARTITION_PATH);
        this.codec = SnapshotCodec.yaml(MasteryLayerCodec::encode, MasteryLayerCodec::decode);
    }

    public @Nullable MasteryLayer read(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return pdcService.readBlob(itemStack, partition, FIELD, codec);
    }

    public boolean write(@Nullable ItemStack itemStack, @Nullable MasteryLayer layer) {
        if (itemStack == null || itemStack.getType().isAir() || layer == null) {
            return false;
        }
        return pdcService.writeBlob(itemStack, partition, FIELD, codec, layer);
    }

    public void clear(@Nullable ItemStack itemStack) {
        if (itemStack != null && !itemStack.getType().isAir()) {
            pdcService.remove(itemStack, partition, FIELD);
        }
    }

    private static Map<String, Object> encode(MasteryLayer layer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(SnapshotCodec.SCHEMA_VERSION_FIELD, SCHEMA_VERSION);
        data.put("instance_id", layer.instanceId());
        data.put("total_exp", layer.totalExp());
        data.put("soft_cap", layer.softCap());
        data.put("level", layer.level());
        data.put("current_exp", layer.currentExp());
        data.put("milestones", new ArrayList<>(layer.milestones()));
        data.put("data_version", layer.dataVersion());
        return data;
    }

    private static MasteryLayer decode(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        Set<Integer> milestones = new LinkedHashSet<>();
        if (data.get("milestones") instanceof Iterable<?> iterable) {
            for (Object raw : iterable) {
                Integer value = Numbers.tryParseInt(raw, null);
                if (value != null && value > 0) {
                    milestones.add(value);
                }
            }
        }
        return new MasteryLayer(
                data.get("instance_id") == null ? "" : String.valueOf(data.get("instance_id")),
                Numbers.tryParseDouble(data.get("total_exp"), 0D),
                Numbers.tryParseInt(data.get("soft_cap"), 0),
                milestones,
                Numbers.tryParseInt(data.get("data_version"), 1));
    }

    public static @NotNull String partitionPath() {
        return PARTITION_PATH;
    }
}
