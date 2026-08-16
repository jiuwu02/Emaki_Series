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

/**
 * 监听 {@link CreatureSpawnEvent}，对 id 与原版 EntityType 同名（typeOverride=true）的
 * MobSpec 应用其组件和属性覆盖，使该原版类型的所有自然刷新生物自动获得自定义配置。
 *
 * <p>事件优先级为 {@link EventPriority#MONITOR}，确保在其他插件完成判断后才介入；
 * 已取消的事件不处理（ignoreCancelled = true）。
 *
 * <p>YAML 示例（对所有自然僵尸应用双倍血量）：
 * <pre>
 * id: zombie
 * type: zombie
 * display_name: "&lt;red&gt;加强僵尸"
 * components:
 *   max_health: 40
 * </pre>
 */
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
