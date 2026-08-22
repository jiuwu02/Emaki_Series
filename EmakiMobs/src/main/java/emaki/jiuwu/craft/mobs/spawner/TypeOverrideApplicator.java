package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.AttributeBridge;
import emaki.jiuwu.craft.mobs.service.ComponentMapper;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Map;
import java.util.function.Supplier;

public final class TypeOverrideApplicator implements Listener {

    private final Supplier<Map<String, MobSpec>> registry;
    private final ComponentMapper componentMapper;
    private final AttributeBridge attributeBridge;
    private final MobIdentifier mobIdentifier;

    public TypeOverrideApplicator(Supplier<Map<String, MobSpec>> registry,
                                   ComponentMapper componentMapper,
                                   AttributeBridge attributeBridge,
                                   MobIdentifier mobIdentifier) {
        this.registry = registry;
        this.componentMapper = componentMapper;
        this.attributeBridge = attributeBridge;
        this.mobIdentifier = mobIdentifier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String typeName = event.getEntityType().name().toLowerCase();
        MobSpec spec = registry.get().get(typeName);
        if (spec == null || !spec.typeOverride()) return;
        mobIdentifier.mark(entity, typeName);
        componentMapper.apply(entity, spec.components());
        attributeBridge.apply(entity, spec.attributes());
    }
}
