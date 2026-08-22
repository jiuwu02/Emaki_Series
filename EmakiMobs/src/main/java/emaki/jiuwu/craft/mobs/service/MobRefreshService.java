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
