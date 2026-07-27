package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashSet;
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
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.text.Texts;

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
        if (critical ? !config.criticalEnabled() : !config.normalEnabled()) {
            return;
        }
        show(target, attacker, critical ? ID_CRITICAL : ID_NORMAL,
                critical ? "critical" : "normal", damage, true);
    }

    /**
     * 展示一次生命恢复。
     *
     * @param beneficiary 获得治疗的实体，吸血时即攻击者
     * @param amount      恢复量
     */
    public void showHeal(LivingEntity beneficiary, double amount) {
        DamageIndicatorConfig config = config();
        if (config == null || !config.enabled() || !config.healEnabled() || amount <= 0D) {
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
        if (config == null || !config.enabled() || !config.dodgeEnabled()) {
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
        service.upsert(new TextDisplaySpec(
                new DisplayKey(NAMESPACE, target.getUniqueId().toString(), id),
                text,
                anchor(config, target),
                null,
                config.lifetimeTicks(),
                viewers(config, target, attacker)
        ));
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

    private Location anchor(DamageIndicatorConfig config, LivingEntity target) {
        Location base = target.getLocation().clone();
        double spread = config.spread();
        if (spread > 0D) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            base.add(random.nextDouble(-spread, spread), 0D, random.nextDouble(-spread, spread));
        }
        return base.add(0D, config.offsetY(), 0D);
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
        return String.format(java.util.Locale.ROOT, "%.1f", value);
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
