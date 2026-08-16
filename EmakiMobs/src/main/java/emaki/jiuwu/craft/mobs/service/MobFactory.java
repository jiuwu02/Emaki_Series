package emaki.jiuwu.craft.mobs.service;

import emaki.jiuwu.craft.mobs.display.BossBarManager;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.skill.MobSkillExecutor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class MobFactory {

    private final Supplier<Map<String, MobSpec>> registry;
    private final ComponentMapper componentMapper;
    private final MobIdentifier mobIdentifier;
    private final AttributeBridge attributeBridge;

    @Nullable
    private MobSkillExecutor skillExecutor;

    @Nullable
    private BossBarManager bossBarManager;

    public MobFactory(Supplier<Map<String, MobSpec>> registry,
                      ComponentMapper componentMapper,
                      MobIdentifier mobIdentifier,
                      AttributeBridge attributeBridge) {
        this.registry = registry;
        this.componentMapper = componentMapper;
        this.mobIdentifier = mobIdentifier;
        this.attributeBridge = attributeBridge;
    }

    /** 延迟注入，解决 MobFactory ← MobSkillExecutor 创建顺序依赖。 */
    public void setSkillExecutor(@Nullable MobSkillExecutor skillExecutor) {
        this.skillExecutor = skillExecutor;
    }

    /** 延迟注入，解决 MobFactory ← BossBarManager 创建顺序依赖。 */
    public void setBossBarManager(@Nullable BossBarManager bossBarManager) {
        this.bossBarManager = bossBarManager;
    }

    public Optional<LivingEntity> spawn(Location location, String mobId) {
        MobSpec spec = registry.get().get(mobId);
        if (spec == null) {
            return Optional.empty();
        }
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        Entity spawned = world.spawnEntity(location, spec.entityType());
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            return Optional.empty();
        }
        mobIdentifier.mark(entity, mobId);
        componentMapper.apply(entity, spec.components());
        attributeBridge.apply(entity, spec.attributes());
        if (bossBarManager != null) bossBarManager.registerIfConfigured(entity, mobId);
        if (skillExecutor != null) skillExecutor.executeForTrigger(entity, mobId, "on_spawn");
        return Optional.of(entity);
    }
}
