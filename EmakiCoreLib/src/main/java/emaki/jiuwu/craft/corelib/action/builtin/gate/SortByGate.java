package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class SortByGate extends BaseGate {

    private static final String DISTANCE = "distance";
    private static final String HEALTH = "health";

    public SortByGate() {
        super("sort_by", "Orders the target flow by distance or health.",
                CoreGateThread.NEEDS_ENTITY_READ,
                CoreStageParameter.positional("key", CoreStageParameterType.STRING,
                        "distance or health"),
                CoreStageParameter.optional("order", CoreStageParameterType.STRING, "asc",
                        "asc or desc"));
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        String key = Texts.lower(arguments.getString("key"));
        if (!DISTANCE.equals(key) && !HEALTH.equals(key)) {
            return CoreGateResult.invalid("action.gate.sort_by.unknown_key", Map.of("key", key));
        }
        String order = Texts.lower(arguments.getString("order", "asc"));
        if (!"asc".equals(order) && !"desc".equals(order)) {
            return CoreGateResult.invalid("action.gate.sort_by.unknown_order", Map.of("order", order));
        }
        if (inbound.size() <= 1) {
            return CoreGateResult.passed(new ArrayList<>(inbound));
        }
        Location origin = origin(context);
        Comparator<CoreActionSubject> comparator = DISTANCE.equals(key)
                ? Comparator.comparingDouble(subject -> distance(subject, origin))
                : Comparator.comparingDouble(SortByGate::health);
        if ("desc".equals(order)) {
            comparator = comparator.reversed();
        }
        List<CoreActionSubject> sorted = new ArrayList<>(inbound);
        sorted.sort(comparator);
        return CoreGateResult.passed(sorted);
    }

    private static Location origin(CoreStageContext context) {
        try {
            return context.origin();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static double distance(CoreActionSubject subject, Location origin) {
        Location location = subject == null ? null : subject.location();
        if (location == null || origin == null || location.getWorld() == null
                || origin.getWorld() == null || !location.getWorld().equals(origin.getWorld())) {

            return Double.MAX_VALUE;
        }
        return location.distanceSquared(origin);
    }

    private static double health(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof LivingEntity living
                ? living.getHealth()
                : Double.MAX_VALUE;
    }
}
