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

/**
 * 生成伤害飘字。
 *
 * <p>只负责取数、限流、合并与文本渲染；实体的创建与到期回收全部委托给
 * CoreLib 的 {@link TextDisplayService}，因此这里不持有任何实体或定时器。
 *
 * <p>合并策略：同一目标的同一类飘字共用一个 {@link DisplayKey}，窗口内的多次伤害
 * 累加后重新 upsert 同一 key，由展示服务刷新文本并重排到期时间。这样多重射击或
 * 同批次多段命中会显示为一个累计数字，而不是一堆互相重叠的实体。
 */
public final class DamageIndicatorService {

    /** 本模块在展示实体服务中的命名空间。 */
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

    /**
     * 展示一次普通或暴击伤害。
     *
     * @param target   受击者
     * @param attacker 攻击者，可为空
     * @param damage   最终伤害
     * @param critical 是否暴击
     */
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

    /**
     * 展示一次生命恢复。
     *
     * @param beneficiary 获得治疗的实体，吸血时即攻击者
     * @param amount      恢复量
     */
    public void showHeal(LivingEntity beneficiary, double amount) {
        DamageIndicatorConfig config = config();
        if (config == null || !config.enabled() || !config.settingsFor(ID_HEAL).enabled() || amount <= 0D) {
            return;
        }
        show(beneficiary, beneficiary, ID_HEAL, "heal", amount, true);
    }

    /**
     * 展示一次闪避。
     *
     * @param target   闪避者
     * @param attacker 攻击者，可为空
     */
    public void showDodge(LivingEntity target, LivingEntity attacker) {
        DamageIndicatorConfig config = config();
        if (config == null || !config.enabled() || !config.settingsFor(ID_DODGE).enabled()) {
            return;
        }
        show(target, attacker, ID_DODGE, "dodge", 0D, false);
    }

    /** 清空全部待合并状态与限流计数。 */
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

    /**
     * 组装抛物运动。
     *
     * <p>{@code durationTicks} 取该触发器的存活时长，使运动与实体回收同时结束。
     */
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

    /**
     * 解析抛出方向为单位向量。
     *
     * <p>{@code away_from_attacker} 取攻击者到目标的水平朝向再叠加 pitch 仰角；
     * 缺攻击者或两者重合时退化为 {@code random}，避免出现零向量。
     */
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

    /** {@return 单位化后的向量；零向量归一为正上方} */
    private DisplayGeometry.Vector3 normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length <= 1.0E-6D) {
            return new DisplayGeometry.Vector3(0D, 1D, 0D);
        }
        return new DisplayGeometry.Vector3(x / length, y / length, z / length);
    }

    /**
     * 在合并窗口内累加伤害。
     *
     * @return 累计值；窗口关闭时返回本次值
     */
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

    /** {@return 是否未超过每目标每秒上限} */
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

    /** {@return 飘字基准位置，为目标脚部加固定偏移再加三轴随机散布} */
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

    /** {@return 在 ±range 内取随机值；range 不为正时返回 0} */
    private double randomSpread(double range) {
        if (range <= 0D) {
            return 0D;
        }
        return ThreadLocalRandom.current().nextDouble(-range, range);
    }

    /**
     * 计算定向可见集合。
     *
     * <p>返回空集表示按距离的空间可见性。真实体后端会忽略该集合。
     */
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
