package emaki.jiuwu.craft.mobs.service;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 在插件重载后对已加载世界中的所有受管生物重新应用组件和属性。
 *
 * <p>扫描所有已加载世界中的存量生物：若该生物带有 {@code mob_id} PDC 且仍有对应定义，
 * 则重新调用 {@link ComponentMapper#apply} 与 {@link AttributeBridge#apply}，
 * 使修改后的 YAML 配置立即对存量实体生效，无需等待重新刷新。
 */
public final class MobRefreshService {

    private final MobIdentifier mobIdentifier;
    private final ComponentMapper componentMapper;
    private final AttributeBridge attributeBridge;
    private final Supplier<Map<String, MobSpec>> registry;

    public MobRefreshService(MobIdentifier mobIdentifier,
                              ComponentMapper componentMapper,
                              AttributeBridge attributeBridge,
                              Supplier<Map<String, MobSpec>> registry) {
        this.mobIdentifier = mobIdentifier;
        this.componentMapper = componentMapper;
        this.attributeBridge = attributeBridge;
        this.registry = registry;
    }

    /**
     * 扫描所有已加载世界，对仍有定义的受管生物重新应用组件与属性。
     *
     * <p><b>注意</b>：此方法应在主线程上调用（BukkitScheduler 的 sync 任务
     * 或直接在 {@code reload()} 末尾调用均可）。
     *
     * @return 实际刷新的生物数量
     */
    public int refreshAll() {
        int count = 0;
        Map<String, MobSpec> current = registry.get();
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                String mobId = mobIdentifier.readId(living);
                if (mobId == null) continue;
                MobSpec spec = current.get(mobId);
                if (spec == null) continue;
                componentMapper.apply(living, spec.components());
                attributeBridge.apply(living, spec.attributes());
                count++;
            }
        }
        return count;
    }
}
