package emaki.jiuwu.craft.strengthen.enhancement.mastery;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;

public record MasteryLayer(@NotNull String instanceId,
        double totalExp,
        int softCap,
        @NotNull Set<Integer> milestones,
        int dataVersion) {

    public static final double EXP_PER_LEVEL = 100D;

    public MasteryLayer {
        instanceId = Texts.toStringSafe(instanceId);
        totalExp = Double.isFinite(totalExp) ? Math.max(0D, totalExp) : 0D;
        softCap = Math.max(0, softCap);
        milestones = milestones == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(milestones));
        dataVersion = Math.max(0, dataVersion);
    }

    public static @NotNull MasteryLayer empty(@Nullable String instanceId, int softCap) {
        return new MasteryLayer(Texts.toStringSafe(instanceId), 0D, softCap, Set.of(), 1);
    }

    public int level() {
        int derived = (int) Math.floor(totalExp / EXP_PER_LEVEL);
        return softCap > 0 ? Math.min(softCap, derived) : derived;
    }

    public double currentExp() {
        return totalExp - level() * EXP_PER_LEVEL;
    }

    public @NotNull MasteryLayer withGainedExp(double delta) {
        if (!Double.isFinite(delta) || delta <= 0D) {
            return this;
        }
        double next = totalExp + delta;
        MasteryLayer advanced = new MasteryLayer(instanceId, next, softCap, milestones, dataVersion);
        int reached = advanced.level();
        if (reached <= level()) {
            return advanced;
        }
        Set<Integer> nextMilestones = new LinkedHashSet<>(milestones);
        for (int candidate = level() + 1; candidate <= reached; candidate++) {
            nextMilestones.add(candidate);
        }
        return new MasteryLayer(instanceId, next, softCap, nextMilestones, dataVersion);
    }

    public @NotNull MasteryLayer withInstanceId(@Nullable String value) {
        return new MasteryLayer(Texts.toStringSafe(value), totalExp, softCap, milestones, dataVersion);
    }

    public @NotNull MasteryLayer withSoftCap(int value) {
        return new MasteryLayer(instanceId, totalExp, value, milestones, dataVersion);
    }

    public @NotNull ItemMasteryView toView() {
        return new ItemMasteryView(instanceId, currentExp(), totalExp, level(), softCap, milestones, dataVersion);
    }
}
