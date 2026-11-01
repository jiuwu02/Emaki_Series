package emaki.jiuwu.craft.dungeon.drop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class DropAllocator {

    public static final NamespacedKey LEVEL_KEY = new NamespacedKey("emakidungeon", "drop_level");

    private record Kind(ItemStack template, int count, int levelWeight) {
    }

    public record KindSpec(int count, int levelWeight) {
    }

    public record Planned(int ownerIndex, int kindIndex, int amount) {
    }

    private DropAllocator() {
    }

    public static List<Planned> plan(List<KindSpec> kinds, int memberCount) {
        if (kinds == null || kinds.isEmpty() || memberCount < 1) {
            return List.of();
        }
        if (kinds.size() == 1) {
            return planSplitByScore(kinds.get(0), memberCount);
        }
        return planAssignKinds(kinds, memberCount);
    }

    private static List<Planned> planSplitByScore(KindSpec kind, int memberCount) {
        int totalWeight = memberCount * (memberCount + 1) / 2;
        List<Planned> planned = new ArrayList<>();
        int assigned = 0;
        List<Integer> amounts = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            int weight = memberCount - i;
            int share = kind.count() * weight / totalWeight;
            amounts.add(share);
            assigned += share;
        }
        int remainder = kind.count() - assigned;
        for (int i = 0; i < memberCount && remainder > 0; i++) {
            if (amounts.get(i) < kind.count()) {
                amounts.set(i, amounts.get(i) + 1);
                remainder--;
            }
        }
        for (int i = 0; i < memberCount; i++) {
            if (amounts.get(i) > 0) {
                planned.add(new Planned(i, 0, amounts.get(i)));
            }
        }
        return planned;
    }

    private static List<Planned> planAssignKinds(List<KindSpec> kinds, int memberCount) {
        List<Planned> planned = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        if (kinds.size() >= memberCount) {
            int low = 0;
            int high = memberCount - 1;
            while (low <= high) {
                order.add(low++);
                if (low <= high) {
                    order.add(high--);
                }
            }
        }
        for (int i = 0; i < kinds.size(); i++) {
            int owner = kinds.size() >= memberCount ? order.get(i % order.size()) : i;
            planned.add(new Planned(owner, i, kinds.get(i).count()));
        }
        return planned;
    }

    public static List<Planned> planShare(List<KindSpec> kinds, int memberCount) {
        if (kinds == null || kinds.isEmpty() || memberCount < 1) {
            return List.of();
        }
        List<Planned> planned = new ArrayList<>();
        for (int owner = 0; owner < memberCount; owner++) {
            for (int kindIndex = 0; kindIndex < kinds.size(); kindIndex++) {
                planned.add(new Planned(owner, kindIndex, kinds.get(kindIndex).count()));
            }
        }
        return planned;
    }

    public static Map<UUID, List<ItemStack>> allocate(List<ItemStack> pool,
            Map<UUID, Double> scores,
            Map<String, Integer> levels,
            List<UUID> members) {
        if (pool == null || pool.isEmpty() || members == null || members.isEmpty()) {
            return Map.of();
        }
        List<UUID> ranked = new ArrayList<>(members);
        ranked.sort((left, right) -> Double.compare(
                scoreOf(scores, right), scoreOf(scores, left)));
        List<Kind> kinds = group(pool, levels);
        if (kinds.isEmpty()) {
            return Map.of();
        }
        List<KindSpec> specs = new ArrayList<>(kinds.size());
        for (Kind kind : kinds) {
            specs.add(new KindSpec(kind.count(), kind.levelWeight()));
        }
        Map<UUID, List<ItemStack>> result = new LinkedHashMap<>();
        for (Planned planned : plan(specs, ranked.size())) {
            Kind kind = kinds.get(planned.kindIndex());
            result.computeIfAbsent(ranked.get(planned.ownerIndex()),
                    key -> new ArrayList<>())
                    .addAll(splitStacks(kind.template(), planned.amount()));
        }
        result.values().removeIf(List::isEmpty);
        return result;
    }

    public static Map<UUID, List<ItemStack>> allocateShare(List<ItemStack> pool, List<UUID> members) {
        Map<UUID, List<ItemStack>> result = new LinkedHashMap<>();
        if (pool == null || pool.isEmpty() || members == null || members.isEmpty()) {
            return result;
        }
        List<Kind> kinds = group(pool, Map.of());
        if (kinds.isEmpty()) {
            return result;
        }
        List<KindSpec> specs = new ArrayList<>(kinds.size());
        for (Kind kind : kinds) {
            specs.add(new KindSpec(kind.count(), kind.levelWeight()));
        }
        for (Planned planned : planShare(specs, members.size())) {
            Kind kind = kinds.get(planned.kindIndex());
            result.computeIfAbsent(members.get(planned.ownerIndex()),
                    key -> new ArrayList<>())
                    .addAll(splitStacks(kind.template(), planned.amount()));
        }
        return result;
    }

    private static List<Kind> group(List<ItemStack> pool, Map<String, Integer> levels) {
        List<Kind> kinds = new ArrayList<>();
        for (ItemStack stack : pool) {
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            Kind matched = null;
            for (Kind kind : kinds) {
                if (kind.template().isSimilar(stack)) {
                    matched = kind;
                    break;
                }
            }
            if (matched == null) {
                kinds.add(new Kind(stack.clone(), stack.getAmount(), levelWeight(stack, levels)));
            } else {
                kinds.set(kinds.indexOf(matched),
                        new Kind(matched.template(), matched.count() + stack.getAmount(),
                                matched.levelWeight()));
            }
        }
        kinds.sort((left, right) -> Integer.compare(right.levelWeight(), left.levelWeight()));
        return kinds;
    }

    private static int levelWeight(ItemStack stack, Map<String, Integer> levels) {
        if (stack == null || !stack.hasItemMeta()) {
            return 1;
        }
        String level = stack.getItemMeta().getPersistentDataContainer()
                .get(LEVEL_KEY, PersistentDataType.STRING);
        if (level == null || levels == null) {
            return 1;
        }
        Integer weight = levels.get(level);
        return weight == null || weight < 1 ? 1 : weight;
    }

    private static List<ItemStack> splitStacks(ItemStack template, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int max = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(remaining, max);
            ItemStack stack = template.clone();
            stack.setAmount(size);
            stacks.add(stack);
            remaining -= size;
        }
        return stacks;
    }

    private static double scoreOf(Map<UUID, Double> scores, UUID memberId) {
        if (scores == null) {
            return 0.0D;
        }
        return scores.getOrDefault(memberId, 0.0D);
    }
}
