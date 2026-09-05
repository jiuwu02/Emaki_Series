package emaki.jiuwu.craft.corelib.matcher;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public record MaterialAllocation(boolean satisfied,
        @NotNull List<Assignment> assignments,
        @NotNull List<Shortage> shortages) {

    public MaterialAllocation {
        assignments = List.copyOf(assignments);
        shortages = List.copyOf(shortages);
    }

    public static @NotNull MaterialAllocation success(@NotNull List<Assignment> assignments) {
        return new MaterialAllocation(true, assignments, List.of());
    }

    public static @NotNull MaterialAllocation failure(@NotNull List<Assignment> assignments,
            @NotNull List<Shortage> shortages) {
        return new MaterialAllocation(false, assignments, shortages);
    }

    public int totalShortage() {
        int total = 0;
        for (Shortage shortage : shortages) {
            total += shortage.missing();
        }
        return total;
    }

    public @NotNull Map<ItemStack, Integer> consumedAmounts() {
        Map<ItemStack, Integer> consumed = new IdentityHashMap<>();
        for (Assignment assignment : assignments) {
            consumed.merge(assignment.stack(), assignment.amount(), Integer::sum);
        }
        return consumed;
    }

    public record Assignment(int requirementIndex,
            @NotNull ItemStack stack,
            int amount,
            @NotNull String materialId,
            @NotNull String requirementId,
            @NotNull String countKey,
            @NotNull String slotId,
            @NotNull String auditId) {
        public Assignment {
            amount = Math.max(0, amount);
            materialId = normalize(materialId);
            requirementId = normalize(requirementId);
            countKey = normalize(countKey);
            slotId = normalize(slotId);
            auditId = normalize(auditId);
        }

        public Assignment(int requirementIndex, @NotNull ItemStack stack, int amount) {
            this(requirementIndex, stack, amount, "", "", "", "", "");
        }

        public @NotNull String identity() {
            return materialId.isBlank() ? requirementId : materialId;
        }
    }

    public record Shortage(int requirementIndex,
            int required,
            int allocated,
            @NotNull String materialId,
            @NotNull String requirementId,
            @NotNull String countKey,
            @NotNull String slotId,
            @NotNull String auditId) {
        public Shortage {
            required = Math.max(0, required);
            allocated = Math.max(0, allocated);
            materialId = normalize(materialId);
            requirementId = normalize(requirementId);
            countKey = normalize(countKey);
            slotId = normalize(slotId);
            auditId = normalize(auditId);
        }

        public Shortage(int requirementIndex, int required, int allocated) {
            this(requirementIndex, required, allocated, "", "", "", "", "");
        }

        public @NotNull String identity() {
            return materialId.isBlank() ? requirementId : materialId;
        }

        public int missing() {
            return Math.max(0, required - allocated);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
