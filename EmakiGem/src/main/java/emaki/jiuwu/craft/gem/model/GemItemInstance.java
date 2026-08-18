package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;

/**
 * 一枚宝石在物品上的实例数据。
 *
 * <p>{@code gemId} 指向宝石定义，其余字段是这一枚宝石独有的运行时状态：{@code instanceId}
 * 唯一标识该实例（镶嵌、取出、交易、重启后保持不变），{@code stage} 表示阶段，
 * {@code affixes} / {@code matrices} 保存词条与矩阵，{@code extensions} 按插件命名空间存放
 * 第三方数据，{@code dataVersion} 供后续结构变更做迁移判断。
 *
 * <p>整体通过 {@link #toMap()} / {@link #fromMap(Map)} 往返持久化，因此新增字段会自动随
 * {@code GemState} 的 socket_assignments 一起落盘。
 */
public record GemItemInstance(String gemId,
        int level,
        long updatedAt,
        String instanceId,
        int stage,
        List<String> affixes,
        Map<String, String> matrices,
        Map<String, Map<String, String>> extensions,
        int dataVersion) {

    /** 当前实例数据结构版本。结构变更时递增，供迁移逻辑判断。 */
    public static final int CURRENT_DATA_VERSION = 1;

    /** 独立宝石物品上的完整实例集合快照；兼容旧标量 PDC 字段作为回退来源。 */
    public static final SnapshotCodec<GemItemInstance> CODEC = SnapshotCodec.versionedYaml(
            CURRENT_DATA_VERSION,
            GemItemInstance::toMap,
            GemItemInstance::fromMap
    );

    public GemItemInstance {
        gemId = Texts.lower(gemId);
        level = Math.max(1, level);
        updatedAt = updatedAt <= 0L ? System.currentTimeMillis() : updatedAt;
        instanceId = Texts.isBlank(instanceId) ? UUID.randomUUID().toString() : Texts.trim(instanceId);
        stage = Math.max(0, stage);
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        matrices = matrices == null ? Map.of() : Map.copyOf(matrices);
        extensions = extensions == null ? Map.of() : copyExtensions(extensions);
        dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;
    }

    /**
     * 以宝石定义与等级创建一枚全新实例，自动生成 {@code instanceId} 并采用当前数据版本。
     *
     * <p>其余实例字段留空，等待镶嵌/升级流程填充。
     */
    public GemItemInstance(String gemId, int level, long updatedAt) {
        this(gemId, level, updatedAt, null, 0, List.of(), Map.of(), Map.of(), CURRENT_DATA_VERSION);
    }

    public String token() {
        return gemId + ":" + level;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gem_id", gemId);
        map.put("level", level);
        map.put("updated_at", updatedAt);
        map.put("instance_id", instanceId);
        map.put("stage", stage);
        map.put("data_version", dataVersion);
        // 空集合不落盘，避免为每枚普通宝石写入三个恒空的容器。
        if (!affixes.isEmpty()) {
            map.put("affixes", List.copyOf(affixes));
        }
        if (!matrices.isEmpty()) {
            map.put("matrices", new LinkedHashMap<>(matrices));
        }
        if (!extensions.isEmpty()) {
            Map<String, Object> serializedExtensions = new LinkedHashMap<>();
            extensions.forEach((namespace, values) -> serializedExtensions.put(namespace, new LinkedHashMap<>(values)));
            map.put("extensions", serializedExtensions);
        }
        return map;
    }

    public static GemItemInstance fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String gemId = Texts.lower(map.get("gem_id"));
        if (Texts.isBlank(gemId)) {
            return null;
        }
        return new GemItemInstance(
                gemId,
                Numbers.tryParseInt(map.get("level"), 1),
                Numbers.tryParseLong(map.get("updated_at"), System.currentTimeMillis()),
                // 旧数据没有 instance_id：紧凑构造器会补一个新的，等价于「首次迁移时分配身份」。
                Texts.toStringSafe(map.get("instance_id")),
                Numbers.tryParseInt(map.get("stage"), 0),
                readStringList(map.get("affixes")),
                readStringMap(map.get("matrices")),
                readExtensions(map.get("extensions")),
                Numbers.tryParseInt(map.get("data_version"), CURRENT_DATA_VERSION)
        );
    }

    public static GemItemInstance fromToken(String token) {
        if (Texts.isBlank(token)) {
            return null;
        }
        String[] parts = Texts.toStringSafe(token).split(":", 2);
        String gemId = Texts.lower(parts[0]);
        if (Texts.isBlank(gemId)) {
            return null;
        }
        int level = parts.length >= 2 ? Numbers.tryParseInt(parts[1], 1) : 1;
        return new GemItemInstance(gemId, level, System.currentTimeMillis());
    }

    private static Map<String, Map<String, String>> copyExtensions(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        source.forEach((namespace, values) -> {
            String key = Texts.lower(namespace);
            if (Texts.isNotBlank(key) && values != null) {
                copy.put(key, Map.copyOf(values));
            }
        });
        return Map.copyOf(copy);
    }

    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object entry : iterable) {
            String value = Texts.toStringSafe(entry);
            if (Texts.isNotBlank(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Map<String, String> readStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = Texts.toStringSafe(entry.getKey());
            if (Texts.isNotBlank(key)) {
                values.put(key, Texts.toStringSafe(entry.getValue()));
            }
        }
        return Map.copyOf(values);
    }

    private static Map<String, Map<String, String>> readExtensions(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String namespace = Texts.lower(entry.getKey());
            if (Texts.isBlank(namespace)) {
                continue;
            }
            Map<String, String> nested = readStringMap(entry.getValue());
            if (!nested.isEmpty()) {
                values.put(namespace, nested);
            }
        }
        return Map.copyOf(values);
    }
}
