package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.config.DamageIndicatorConfig;
import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.DisplayMotion;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class DamageIndicatorService {

    private static final String NAMESPACE = "emakiattribute";

    private static final String ID_NORMAL = "normal";
    private static final String ID_CRITICAL = "critical";
    private static final String ID_HEAL = "heal";
    private static final String ID_DODGE = "dodge";

    private final Supplier<TextDisplayService> displayServiceSupplier;
    private final Supplier<DamageIndicatorConfig> configSupplier;
    private final Supplier<MessageService> messageServiceSupplier;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, RateWindow> rates = new ConcurrentHashMap<>();

    public DamageIndicatorService(Supplier<TextDisplayService> displayServiceSupplier,
            Supplier<DamageIndicatorConfig> configSupplier,
            Supplier<MessageService> messageServiceSupplier) {
        this.displayServiceSupplier = displayServiceSupplier;
        this.configSupplier = configSupplier;
        this.messageServiceSupplier = messageServiceSupplier;
    }

    public void showDamage(LivingEntity target, LivingEntity attacker, double damage, boolean critical) {
        DamageIndicatorConfig config = config();
        if (config == null || !config.enabled() || damage <= 0D) {
            return;
        }
        String id = critical ? ID_CRITICAL : ID_NORMAL;
        if (!config.settingsFor(id).enabled()) {
            return;
        }
        show(target, attacker, id, critical ? "critical" : "normal", damage, true);
    }

    public void showHeal(LivingEntity beneficiary, double amount) {
        DamageIndicatorConfig config = config();
        if (config == null || !config.enabled() || !config.settingsFor(ID_HEAL).enabled() || amount <= 0D) {
            return;
        }
        show(beneficiary, beneficiary, ID_HEAL, "heal", amount, true);
    }

    public void showDodge(LivingEntity target, LivingEntity attacker) {
        DamageIndicatorConfig config = config();
        if (config == null || !config.enabled() || !config.settingsFor(ID_DODGE).enabled()) {
            return;
        }
        show(target, attacker, ID_DODGE, "dodge", 0D, false);
    }

    public void reset() {
        pending.clear();
        rates.clear();
    }

    private void show(LivingEntity target,
            LivingEntity attacker,
            String id,
            String templateKey,
            double amount,
            boolean accumulate) {
        DamageIndicatorConfig config = config();
        TextDisplayService service = displayServiceSupplier.get();
        if (service == null || target == null || target.getLocation().getWorld() == null) {
            return;
        }
        String runtimeKey = target.getUniqueId() + ":" + id;
        double total = accumulate ? accumulate(config, runtimeKey, amount) : amount;
        if (total < 0D) {
            return;
        }
        if (!allowRate(config, target.getUniqueId())) {
            return;
        }
        String text = render(templateKey, total);
        if (Texts.isBlank(text)) {
            return;
        }
        DamageIndicatorConfig.TriggerSettings settings = config.settingsFor(id);
        service.upsert(new TextDisplaySpec(
                new DisplayKey(NAMESPACE, target.getUniqueId().toString(), id),
                text,
                anchor(settings, target),
                null,
                settings.lifetimeTicks(),
                viewers(config, target, attacker),
                buildMotion(settings, target, attacker)
        ));
    }

    private DisplayMotion buildMotion(DamageIndicatorConfig.TriggerSettings settings,
            LivingEntity target,
            LivingEntity attacker) {
        DamageIndicatorConfig.MotionSettings motion = settings.motion();
        if (!motion.enabled()) {
            return DisplayMotion.NONE;
        }
        DisplayGeometry.Vector3 direction = resolveDirection(motion.direction(), target, attacker);
        DamageIndicatorConfig.ScaleSettings scale = motion.scale();
        return new DisplayMotion(
                new DisplayGeometry.Vector3(
                        direction.x() * motion.speed(),
                        direction.y() * motion.speed(),
                        direction.z() * motion.speed()
                ),
                new DisplayGeometry.Vector3(0D, motion.gravity(), 0D),
                settings.lifetimeTicks(),
                motion.stepTicks(),
                scale.popFrom(),
                scale.popTicks(),
                scale.shrinkTo(),
                scale.shrinkTicks()
        );
    }

    private DisplayGeometry.Vector3 resolveDirection(DamageIndicatorConfig.DirectionSettings direction,
            LivingEntity target,
            LivingEntity attacker) {
        if (DamageIndicatorConfig.DirectionSettings.MODE_FIXED.equals(direction.mode())) {
            return normalize(direction.fixed().x(), direction.fixed().y(), direction.fixed().z());
        }
        double pitch = Math.toRadians(direction.pitch().resolve());
        if (DamageIndicatorConfig.DirectionSettings.MODE_AWAY_FROM_ATTACKER.equals(direction.mode())
                && attacker != null
                && target.getWorld().equals(attacker.getWorld())) {
            double awayX = target.getLocation().getX() - attacker.getLocation().getX();
            double awayZ = target.getLocation().getZ() - attacker.getLocation().getZ();
            double horizontal = Math.sqrt(awayX * awayX + awayZ * awayZ);
            if (horizontal > 1.0E-6D) {
                double cos = Math.cos(pitch);
                return new DisplayGeometry.Vector3(
                        awayX / horizontal * cos,
                        Math.sin(pitch),
                        awayZ / horizontal * cos
                );
            }
        }
        double yaw = Math.toRadians(direction.yaw().resolve());
        double cos = Math.cos(pitch);
        return new DisplayGeometry.Vector3(cos * Math.cos(yaw), Math.sin(pitch), cos * Math.sin(yaw));
    }

    private DisplayGeometry.Vector3 normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length <= 1.0E-6D) {
            return new DisplayGeometry.Vector3(0D, 1D, 0D);
        }
        return new DisplayGeometry.Vector3(x / length, y / length, z / length);
    }

    private double accumulate(DamageIndicatorConfig config, String runtimeKey, double amount) {
        if (config.mergeWindowMs() <= 0L) {
            return amount;
        }
        long now = System.currentTimeMillis();
        Pending merged = pending.compute(runtimeKey, (ignored, current) -> {
            if (current == null || now - current.startedAt >= config.mergeWindowMs()) {
                return new Pending(now, amount);
            }
            return new Pending(current.startedAt, current.total + amount);
        });
        return merged.total;
    }

    private boolean allowRate(DamageIndicatorConfig config, UUID targetId) {
        if (config.maxPerTargetPerSecond() <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        RateWindow updated = rates.compute(targetId, (ignored, current) -> {
            if (current == null || now - current.startedAt >= 1000L) {
                return new RateWindow(now, 1);
            }
            return new RateWindow(current.startedAt, current.count + 1);
        });
        return updated.count <= config.maxPerTargetPerSecond();
    }

    private Location anchor(DamageIndicatorConfig.TriggerSettings settings, LivingEntity target) {
        DamageIndicatorConfig.SpawnSettings spawn = settings.spawn();
        Location base = target.getLocation().clone();
        base.add(spawn.offset().x(), spawn.offset().y(), spawn.offset().z());
        return base.add(
                randomSpread(spawn.randomOffset().x()),
                randomSpread(spawn.randomOffset().y()),
                randomSpread(spawn.randomOffset().z())
        );
    }

    private double randomSpread(double range) {
        if (range <= 0D) {
            return 0D;
        }
        return ThreadLocalRandom.current().nextDouble(-range, range);
    }

    private Set<UUID> viewers(DamageIndicatorConfig config, LivingEntity target, LivingEntity attacker) {
        if (!config.visibleToInvolved()) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        if (target instanceof Player player) {
            result.add(player.getUniqueId());
        }
        if (attacker instanceof Player player) {
            result.add(player.getUniqueId());
        }
        return result;
    }

    private String render(String templateKey, double amount) {
        MessageService messageService = messageServiceSupplier.get();
        if (messageService == null) {
            return null;
        }
        return messageService.message("damage.indicator." + templateKey,
                Map.of("damage", format(amount), "amount", format(amount)));
    }

    private String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private DamageIndicatorConfig config() {
        DamageIndicatorConfig config = configSupplier.get();
        return config == null ? DamageIndicatorConfig.defaults() : config;
    }

    private record Pending(long startedAt, double total) {
    }

    private record RateWindow(long startedAt, int count) {
    }
}
