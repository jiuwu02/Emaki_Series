package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class DistanceFilterGate extends BaseGate {

    public DistanceFilterGate() {
        super("distance_filter", "只保留与管道原点距离在 [min, max] 内的目标。",
                CoreGateThread.NEEDS_ENTITY_READ,
                CoreStageParameter.optional("min", CoreStageParameterType.DOUBLE, "0", "最小距离"),
                CoreStageParameter.optional("max", CoreStageParameterType.DOUBLE, "32", "最大距离"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        Location origin;
        try {
            origin = context.origin();
        } catch (IllegalStateException exception) {
            origin = null;
        }
        if (origin == null || origin.getWorld() == null) {
            return CoreGateResult.halted("action.gate.distance_filter.no_origin");
        }
        double min = Math.max(0D, arguments.getDouble("min", 0D));
        double max = Math.max(min, arguments.getDouble("max", 32D));
        List<CoreActionSubject> kept = new ArrayList<>(inbound.size());
        for (CoreActionSubject subject : inbound) {
            if (subject.entityOrNull() == null) {
                continue;
            }
            Location location = subject.entityOrNull().getLocation();
            if (location.getWorld() == null || !location.getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distance = location.distance(origin);
            if (distance >= min && distance <= max) {
                kept.add(subject);
            }
        }
        return CoreGateResult.passed(kept);
    }
}
