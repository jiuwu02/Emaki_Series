package emaki.jiuwu.craft.mobs.service;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.function.Supplier;

public final class MobRefreshService {

    private final MobIdentifier mobIdentifier;
    private final ComponentMapper componentMapper;
    private final Supplier<Map<String, MobSpec>> registry;

    public MobRefreshService(MobIdentifier mobIdentifier,
                              ComponentMapper componentMapper,
                              Supplier<Map<String, MobSpec>> registry) {
        this.mobIdentifier = mobIdentifier;
        this.componentMapper = componentMapper;
        this.registry = registry;
    }

    public boolean refresh(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return false;
        }
        MobSpec spec = registry.get().get(mobId);
        if (spec == null) {
            return false;
        }
        componentMapper.applyForRefresh(entity, spec.components());
        return true;
    }

    public int refreshAll() {
        int count = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living && refresh(living)) {
                    count++;
                }
            }
        }
        return count;
    }
}
