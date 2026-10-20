package emaki.jiuwu.craft.dungeon.listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import emaki.jiuwu.craft.dungeon.flow.EventIngressKeys;
import emaki.jiuwu.craft.dungeon.flow.Facts;
import emaki.jiuwu.craft.dungeon.flow.FlowModules;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class FlowEventListeners implements Listener {

    private final InstanceRegistry registry;

    public FlowEventListeners(InstanceRegistry registry) {
        this.registry = registry;
    }

    private DungeonInstance instanceOf(org.bukkit.World world) {
        return registry.get(world.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL)) {
            return;
        }
        DungeonInstance instance = instanceOf(entity.getWorld());
        if (instance == null) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        Map<String, Object> payload = new HashMap<>();
        payload.put(EventIngressKeys.KEY_ENTITY, entity.getUniqueId());
        payload.put("mob", entity.getType().name().toLowerCase(java.util.Locale.ROOT));
        if (killer != null) {
            payload.put(EventIngressKeys.KEY_PLAYER, killer.getUniqueId());
        }
        instance.ingress().post(new IngressEvent(EventIngressKeys.DEATH,
                entity.getType().name(), payload));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        DungeonInstance instance = instanceOf(event.getPlayer().getWorld());
        if (instance == null) {
            return;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return;
        }
        Block block = event.getClickedBlock();
        String click = resolveClick(event);
        if (click == null) {
            return;
        }
        String interactionId = findBlockInteraction(runtime, block);
        if (interactionId != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put(EventIngressKeys.KEY_PLAYER, event.getPlayer().getUniqueId());
            payload.put(Facts.CLICK, click);
            instance.ingress().post(new IngressEvent(EventIngressKeys.BLOCK, interactionId, payload));
            return;
        }
        String itemId = findItemInteraction(runtime, block);
        if (itemId != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put(EventIngressKeys.KEY_PLAYER, event.getPlayer().getUniqueId());
            payload.put(Facts.ITEM_INTERACTED, true);
            instance.ingress().post(new IngressEvent(EventIngressKeys.ITEM, itemId, payload));
            return;
        }
        String redstoneMechanism = findRedstoneMechanism(runtime, block);
        if (redstoneMechanism != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put(EventIngressKeys.KEY_PLAYER, event.getPlayer().getUniqueId());
            payload.put(Facts.BLOCK_ACTIVATE, true);
            instance.ingress().post(new IngressEvent(EventIngressKeys.ACTIVATE, redstoneMechanism, payload));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        DungeonInstance instance = instanceOf(event.getEntity().getWorld());
        if (instance == null) {
            return;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return;
        }
        Block block = event.getBlock();
        String mechanismId = findRedstoneMechanism(runtime, block);
        if (mechanismId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (event.getEntity() instanceof Player player) {
            payload.put(EventIngressKeys.KEY_PLAYER, player.getUniqueId());
        }
        payload.put(Facts.BLOCK_ACTIVATE, true);
        instance.ingress().post(new IngressEvent(EventIngressKeys.ACTIVATE, mechanismId, payload));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        DungeonInstance instance = instanceOf(event.getBlock().getWorld());
        if (instance == null) {
            return;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return;
        }
        String mechanismId = findRedstoneMechanism(runtime, event.getBlock());
        if (mechanismId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(Facts.REDSTONE_LEVEL, event.getNewCurrent() > 0 ? "high" : "low");
        instance.ingress().post(new IngressEvent(EventIngressKeys.REDSTONE, mechanismId, payload));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        handleLeftClick(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        DungeonInstance instance = instanceOf(event.getPlayer().getWorld());
        if (instance == null) {
            return;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return;
        }
        String interactionId = findBlockInteraction(runtime, event.getBlock());
        if (interactionId != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put(EventIngressKeys.KEY_PLAYER, event.getPlayer().getUniqueId());
            payload.put(Facts.CLICK, "place");
            instance.ingress().post(new IngressEvent(EventIngressKeys.BLOCK, interactionId, payload));
        }
    }

    private void handleLeftClick(Player player, Block block) {
        DungeonInstance instance = instanceOf(player.getWorld());
        if (instance == null) {
            return;
        }
        FlowRuntime runtime = instance.flowRuntime();
        if (runtime == null) {
            return;
        }
        String interactionId = findBlockInteraction(runtime, block);
        if (interactionId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(EventIngressKeys.KEY_PLAYER, player.getUniqueId());
        payload.put(Facts.CLICK, player.isSneaking() ? "sneak_left" : "left");
        instance.ingress().post(new IngressEvent(EventIngressKeys.BLOCK, interactionId, payload));
    }

    private String resolveClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return null;
        }
        boolean sneak = event.getPlayer().isSneaking();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            return sneak ? "sneak_right" : "right";
        }
        return sneak ? "sneak_left" : "left";
    }

    private String findBlockInteraction(FlowRuntime runtime, Block block) {
        for (Map.Entry<String, FlowModules.BlockInteractionDef> entry
                : runtime.definition().blockInteractions().entrySet()) {
            FlowModules.BlockInteractionDef def = entry.getValue();
            if (def.head().enabled() && matches(def.point(), block)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findItemInteraction(FlowRuntime runtime, Block block) {
        for (Map.Entry<String, FlowModules.ItemInteractionDef> entry
                : runtime.definition().itemInteractions().entrySet()) {
            FlowModules.ItemInteractionDef def = entry.getValue();
            if (def.head().enabled() && matches(def.point(), block)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findRedstoneMechanism(FlowRuntime runtime, Block block) {
        for (Map.Entry<String, FlowModules.MechanismDef> entry : runtime.definition().mechanisms().entrySet()) {
            FlowModules.MechanismDef def = entry.getValue();
            if (!def.head().enabled()) {
                continue;
            }
            for (FlowModules.BlockStateTarget target : def.targets()) {
                if (matches(target.point(), block)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static boolean matches(emaki.jiuwu.craft.dungeon.flow.FlowPoint point, Block block) {
        if (point == null) {
            return false;
        }
        return block.getX() == (int) Math.floor(point.x())
                && block.getY() == (int) Math.floor(point.y())
                && block.getZ() == (int) Math.floor(point.z());
    }
}
