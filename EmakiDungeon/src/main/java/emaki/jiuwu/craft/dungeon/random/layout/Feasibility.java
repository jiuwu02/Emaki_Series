package emaki.jiuwu.craft.dungeon.random.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.gen.Topology;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;

/**
 * 生成前可行性预检。
 *
 * <p>拓扑是一棵树: roomCount 个节点有 roomCount-1 条边, 度数和 = 2*(roomCount-1)。树的叶节点度 1,
 * 其余节点度 >= 2。因此至少有 (roomCount-2) 个节点需要度 >= 2, 且平均度接近 2。
 *
 * <p>每个门锚点只能接一条边, 所以节点可用度数上限 = 该模板的门锚点数。若某个房型被强制分配到
 * 多个节点, 它的门锚点数会构成硬上限; 全部节点都必须能提供至少 1 个门, 否则无解。
 */
public final class Feasibility {

    /** 预检结论: 可行 / 不可行(附具体缺口) / 无法判定(数据不足)。 */
    public record Verdict(boolean feasible, String reasonKey, Map<String, String> params) {

        public static Verdict ok() {
            return new Verdict(true, "", Map.of());
        }

        public static Verdict fail(String reasonKey, Map<String, String> params) {
            return new Verdict(false, reasonKey, params);
        }
    }

    private Feasibility() {
    }

    /**
     * 按拓扑的真实度数序列, 逐节点检查「候选模板中存在一个门锚点数达标且可用旋转方向足够」的方案。
     *
     * <p>只做必要条件的检查; 通过的配置仍可能在布局阶段失败, 但通不过的一定失败。
     */
    public static Verdict check(Topology topology, List<String> candidateTypeIds,
            Map<String, TemplateMeta> metas, RandomConfig config) {
        if (topology == null || topology.nodes().isEmpty()) {
            return Verdict.fail("random.feasibility_no_topology", Map.of());
        }
        Map<Integer, Integer> degrees = degreesOf(topology);
        List<Integer> sorted = new ArrayList<>(degrees.values());
        sorted.sort(Comparator.reverseOrder());
        int maxDegree = sorted.isEmpty() ? 0 : sorted.get(0);

        List<TemplateMeta> candidates = new ArrayList<>();
        for (String typeId : candidateTypeIds) {
            TemplateMeta meta = metas.get(typeId);
            if (meta != null && TemplateModel.TYPE_ROOM.equals(meta.structureType())) {
                candidates.add(meta);
            }
        }
        if (candidates.isEmpty()) {
            return Verdict.fail("random.feasibility_no_room", Map.of());
        }
        int bestAnchors = 0;
        for (TemplateMeta meta : candidates) {
            bestAnchors = Math.max(bestAnchors, meta.anchors().size());
        }
        if (bestAnchors < maxDegree) {
            return Verdict.fail("random.feasibility_degree", Map.of(
                    "need", String.valueOf(maxDegree),
                    "have", String.valueOf(bestAnchors),
                    "rooms", String.valueOf(topology.nodes().size())));
        }
        for (TemplateMeta meta : candidates) {
            if (meta.anchors().isEmpty() && maxDegree > 0) {
                continue;
            }
            if (legalVariantCount(meta, config) == 0) {
                return Verdict.fail("random.feasibility_orientation", Map.of("id", meta.id()));
            }
        }
        // 强制房型: min-count 之和不得超过拓扑节点数
        int forced = 0;
        for (RandomConfig.RoomType type : config.roomTypes.values()) {
            forced += Math.max(0, type.minCount());
        }
        if (forced > topology.nodes().size()) {
            return Verdict.fail("random.feasibility_forced", Map.of(
                    "forced", String.valueOf(forced),
                    "rooms", String.valueOf(topology.nodes().size())));
        }
        return Verdict.ok();
    }

    public static Map<Integer, Integer> degreesOf(Topology topology) {
        return topology.degrees();
    }

    private static int legalVariantCount(TemplateMeta meta, RandomConfig config) {
        boolean rotates = meta.rotatable() && config.orientation.rotatable();
        int turns = rotates ? 4 : 1;
        boolean mirror = meta.mirrorable() && "x".equals(config.orientation.mirrorAxis());
        return turns * (mirror ? 2 : 1);
    }
}
