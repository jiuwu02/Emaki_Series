package emaki.jiuwu.craft.mobs.service;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class MobFactory {

    private final Supplier<Map<String, MobSpec>> registry;
    private final ComponentMapper componentMapper;
    private final MobIdentifier mobIdentifier;
    private final AttributeBridge attributeBridge;

    public MobFactory(Supplier<Map<String, MobSpec>> registry,
                      ComponentMapper componentMapper,
                      MobIdentifier mobIdentifier,
                      AttributeBridge attributeBridge) {
        this.registry = registry;
        this.componentMapper = componentMapper;
        this.mobIdentifier = mobIdentifier;
        this.attributeBridge = attributeBridge;
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
        return Optional.of(entity);
    }
}
