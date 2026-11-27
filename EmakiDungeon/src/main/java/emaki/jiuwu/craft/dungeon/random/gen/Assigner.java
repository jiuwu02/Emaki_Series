package emaki.jiuwu.craft.dungeon.random.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import emaki.jiuwu.craft.dungeon.random.gen.Topology.Node;

public final class Assigner {

    public record TemplateOption(String typeId, List<String> tags, int weight, int minCount, int maxCount,
            int anchorCount) {

        public TemplateOption {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        public boolean carries(String tag) {
            return tags.contains(tag);
        }

        /** 该模板的门锚点数是否够接出 degree 条连接。 */
        public boolean affords(int degree) {
            return anchorCount >= degree;
        }
    }

    public record MandatoryRule(String tag, int minCount, int maxCount, String placementRule, int percentile) {
    }

    public record Result(Map<Integer, String> nodeTemplate, int attempts) {
    }

    private static final int REALLOCATION_LIMIT = 3;

    public Result assign(long seed, Topology topology, List<TemplateOption> options, List<MandatoryRule> rules) {
        Random random = new Random(seed);
        Map<Integer, Integer> depths = topology.depths();
        Map<Integer, String> assignment = new LinkedHashMap<>();
        for (int attempt = 0; attempt < REALLOCATION_LIMIT; attempt++) {
            assignment.clear();
            boolean ok = true;
            Map<String, Integer> placed = new LinkedHashMap<>();
            for (TemplateOption option : options) {
                placed.put(option.typeId(), 0);
            }
            for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                MandatoryRule rule = rules.get(ruleIndex);
                List<Integer> seats = selectSeats(topology, depths, rule, assignment.keySet());
                List<TemplateOption> carriers = new ArrayList<>();
                for (TemplateOption option : options) {
                    if (option.carries(rule.tag())) {
                        carriers.add(option);
                    }
                }
                if (carriers.isEmpty()) {
                    return new Result(Map.of(), attempt + 1);
                }
                carriers.sort((left, right) -> left.typeId().compareTo(right.typeId()));
                int need = Math.max(1, rule.minCount());
                for (int i = 0; i < need && i < seats.size(); i++) {
                    TemplateOption picked = carriers.get(random.nextInt(carriers.size()));
                    if (picked.maxCount() > 0 && placed.getOrDefault(picked.typeId(), 0) >= picked.maxCount()) {
                        picked = carriers.get(0);
                    }
                    if (picked.maxCount() > 0 && placed.getOrDefault(picked.typeId(), 0) >= picked.maxCount()) {
                        ok = false;
                        break;
                    }
                    assignment.put(seats.get(i), picked.typeId());
                    placed.merge(picked.typeId(), 1, Integer::sum);
                }
                if (!ok) {
                    break;
                }
                if (seats.size() < need) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            // 主动保证 room-types 的 min-count: 先按模板固定占用座位, 再走加权抽样
            Map<Integer, Integer> degrees = topology.degrees();
            List<Node> unassigned = new ArrayList<>();
            for (Node node : topology.nodes()) {
                if (!assignment.containsKey(node.id())) {
                    unassigned.add(node);
                }
            }
            for (TemplateOption option : options) {
                int need = option.minCount() - placed.getOrDefault(option.typeId(), 0);
                if (need <= 0) {
                    continue;
                }
                if (option.maxCount() > 0) {
                    need = Math.min(need, option.maxCount() - placed.getOrDefault(option.typeId(), 0));
                }
                while (need > 0 && !unassigned.isEmpty()) {
                    Node seat = takeSeat(unassigned, degrees, option, random);
                    assignment.put(seat.id(), option.typeId());
                    placed.merge(option.typeId(), 1, Integer::sum);
                    need--;
                }
                if (need > 0) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            while (!unassigned.isEmpty()) {
                // 先处理度数最高的节点: 它对门锚点要求最苛刻, 可选模板最少, 留到最后容易无解
                unassigned.sort((left, right) -> {
                    int degreeCompare = Integer.compare(
                            degrees.getOrDefault(right.id(), 0), degrees.getOrDefault(left.id(), 0));
                    return degreeCompare != 0 ? degreeCompare : Integer.compare(left.id(), right.id());
                });
                Node node = unassigned.remove(0);
                int nodeDegree = degrees.getOrDefault(node.id(), 0);
                List<TemplateOption> pool = new ArrayList<>();
                int totalWeight = 0;
                for (TemplateOption option : options) {
                    if (option.weight() <= 0) {
                        continue;
                    }
                    if (option.maxCount() > 0 && placed.getOrDefault(option.typeId(), 0) >= option.maxCount()) {
                        continue;
                    }
                    if (ruleCapped(rules, placed, options, option)) {
                        continue;
                    }
                    if (!option.affords(nodeDegree)) {
                        continue;
                    }
                    pool.add(option);
                    totalWeight += option.weight();
                }
                if (totalWeight <= 0) {
                    ok = false;
                    break;
                }
                int roll = random.nextInt(totalWeight);
                TemplateOption picked = pool.get(pool.size() - 1);
                for (TemplateOption option : pool) {
                    roll -= option.weight();
                    if (roll < 0) {
                        picked = option;
                        break;
                    }
                }
                assignment.put(node.id(), picked.typeId());
                placed.merge(picked.typeId(), 1, Integer::sum);
            }
            if (!ok) {
                continue;
            }
            if (!fitsAnchors(assignment, degrees, options)) {
                continue;
            }
            boolean intervalsOk = true;
            for (TemplateOption option : options) {
                int count = placed.getOrDefault(option.typeId(), 0);
                if (count < option.minCount() || (option.maxCount() > 0 && count > option.maxCount())) {
                    intervalsOk = false;
                    break;
                }
            }
            for (MandatoryRule rule : rules) {
                int count = 0;
                for (String typeId : assignment.values()) {
                    for (TemplateOption option : options) {
                        if (option.typeId().equals(typeId) && option.carries(rule.tag())) {
                            count++;
                            break;
                        }
                    }
                }
                if (count < rule.minCount() || (rule.maxCount() > 0 && count > rule.maxCount())) {
                    intervalsOk = false;
                    break;
                }
            }
            if (intervalsOk) {
                return new Result(Map.copyOf(assignment), attempt + 1);
            }
        }
        return new Result(Map.of(), REALLOCATION_LIMIT);
    }

    /**
     * 从剩余座位里挑一个给该模板: 门锚点够用的座位优先, 同档内随机。
     *
     * <p>门锚点少的模板 (例如只在墙上开一个门的出生房) 必须落在叶节点, 否则布局阶段接不出
     * 第二条连接; 门锚点多的模板反之应该吃掉分叉节点。按度数择座能显著抬高中低门数的通过率。
     */
    private Node takeSeat(List<Node> unassigned, Map<Integer, Integer> degrees,
            TemplateOption option, Random random) {
        List<Node> fits = new ArrayList<>();
        for (Node node : unassigned) {
            if (option.affords(degrees.getOrDefault(node.id(), 0))) {
                fits.add(node);
            }
        }
        List<Node> pool = fits.isEmpty() ? unassigned : fits;
        Node seat = pool.remove(random.nextInt(pool.size()));
        unassigned.remove(seat);
        return seat;
    }

    /** 座位全部定下后复核: 每个节点上的模板门锚点数必须够接出该节点的度数。 */
    private boolean fitsAnchors(Map<Integer, String> assignment, Map<Integer, Integer> degrees,
            List<TemplateOption> options) {
        for (Map.Entry<Integer, String> entry : assignment.entrySet()) {
            int degree = degrees.getOrDefault(entry.getKey(), 0);
            TemplateOption option = optionOf(options, entry.getValue());
            if (option != null && !option.affords(degree)) {
                return false;
            }
        }
        return true;
    }

    private TemplateOption optionOf(List<TemplateOption> options, String typeId) {
        for (TemplateOption option : options) {
            if (option.typeId().equals(typeId)) {
                return option;
            }
        }
        return null;
    }

    private boolean ruleCapped(List<MandatoryRule> rules, Map<String, Integer> placed,
            List<TemplateOption> options, TemplateOption option) {
        for (MandatoryRule rule : rules) {
            if (rule.maxCount() <= 0 || !option.carries(rule.tag())) {
                continue;
            }
            int count = 0;
            for (Map.Entry<String, Integer> entry : placed.entrySet()) {
                for (TemplateOption candidate : options) {
                    if (candidate.typeId().equals(entry.getKey()) && candidate.carries(rule.tag())) {
                        count += entry.getValue();
                        break;
                    }
                }
            }
            if (count >= rule.maxCount()) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> selectSeats(Topology topology, Map<Integer, Integer> depths,
            MandatoryRule rule, java.util.Set<Integer> taken) {
        List<Node> ranked = new ArrayList<>(topology.nodes());
        ranked.sort((left, right) -> {
            int depthCompare = Integer.compare(
                    depths.getOrDefault(left.id(), 0), depths.getOrDefault(right.id(), 0));
            return depthCompare != 0 ? depthCompare : Integer.compare(left.id(), right.id());
        });
        List<Integer> seats = new ArrayList<>();
        String ruleName = rule.placementRule() == null ? "root" : rule.placementRule().toLowerCase(java.util.Locale.ROOT);
        if ("deepest".equals(ruleName)) {
            for (int i = ranked.size() - 1; i >= 0; i--) {
                seats.add(ranked.get(i).id());
            }
        } else if ("branch-percentile".equals(ruleName)) {
            int percentile = Math.max(50, Math.min(70, rule.percentile()));
            int from = ranked.size() * percentile / 100;
            int to = Math.min(ranked.size(), ranked.size() * (percentile + 20) / 100);
            for (int i = from; i < to; i++) {
                seats.add(ranked.get(i).id());
            }
            for (int i = 0; i < ranked.size() && seats.isEmpty(); i++) {
                seats.add(ranked.get(i).id());
            }
        } else {
            for (int i = 0; i < ranked.size(); i++) {
                seats.add(ranked.get(i).id());
            }
        }
        seats.removeAll(taken);
        return seats;
    }
}
