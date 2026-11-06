package emaki.jiuwu.craft.dungeon.bridge;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.dungeon.api.event.DungeonCompleteEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonEnterEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonFailEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonGateOpenEvent;
import emaki.jiuwu.craft.dungeon.api.event.DungeonRoomClearEvent;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.manager.PlaceholderManager;
import kr.toxicity.hud.api.manager.TriggerManager;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import kr.toxicity.hud.api.trigger.HudTrigger;
import kr.toxicity.hud.api.update.UpdateEvent;
import kr.toxicity.hud.api.update.UpdateReason;

public final class DungeonBetterHudBridge implements Listener {

    private final JavaPlugin plugin;
    private final InstanceRegistry registry;
    private final PartyService parties;

    private final List<BiConsumer<UUID, UpdateEvent>> enterConsumers = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> roomClearConsumers = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> gateOpenConsumers = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> completeConsumers = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> failConsumers = new CopyOnWriteArrayList<>();

    public DungeonBetterHudBridge(JavaPlugin plugin, InstanceRegistry registry, PartyService parties) {
        this.plugin = plugin;
        this.registry = registry;
        this.parties = parties;
    }

    public void register() {
        TriggerManager triggers = BetterHud.getInstance().getTriggerManager();
        addTrigger(triggers, "emakidungeon_enter", enterConsumers);
        addTrigger(triggers, "emakidungeon_room_clear", roomClearConsumers);
        addTrigger(triggers, "emakidungeon_gate_open", gateOpenConsumers);
        addTrigger(triggers, "emakidungeon_complete", completeConsumers);
        addTrigger(triggers, "emakidungeon_fail", failConsumers);
        registerSidebarPlaceholders();
    }

    private void registerSidebarPlaceholders() {
        PlaceholderManager manager = BetterHud.getInstance().getPlaceholderManager();
        manager.getBooleanContainer().addPlaceholder("emakidungeon_in_party",
                HudPlaceholder.<Boolean>of((args, reason) -> hud -> parties.partyOf(hud.uuid()) != null));
        manager.getNumberContainer().addPlaceholder("emakidungeon_party_size",
                HudPlaceholder.<Number>of((args, reason) -> hud -> {
                    Party party = parties.partyOf(hud.uuid());
                    return party == null ? 0 : party.size();
                }));
    }

    private static void addTrigger(TriggerManager triggers, String name,
            List<BiConsumer<UUID, UpdateEvent>> consumers) {
        triggers.addTrigger(name, yaml -> new HudTrigger<Object>() {
            @Override
            public Object getKey(Object o) {
                return UUID.randomUUID();
            }

            @Override
            public void registerEvent(BiConsumer<UUID, UpdateEvent> consumer) {
                consumers.add(consumer);
            }
        });
    }

    private static void fire(List<BiConsumer<UUID, UpdateEvent>> consumers, UUID playerId) {
        if (consumers.isEmpty()) {
            return;
        }
        UpdateEvent event = new UpdateEvent() {
            private final Object key = UUID.randomUUID();

            @Override
            public Object getKey() {
                return key;
            }

            @Override
            public UpdateReason getType() {
                return UpdateReason.OTHER;
            }
        };
        for (BiConsumer<UUID, UpdateEvent> consumer : consumers) {
            consumer.accept(playerId, event);
        }
    }

    private void fireParty(String instanceId, List<BiConsumer<UUID, UpdateEvent>> consumers) {
        DungeonInstance instance = registry.get(instanceId);
        if (instance == null) {
            return;
        }
        Party party = instance.partyId() == null ? null : parties.partyById(instance.partyId());
        if (party == null) {
            return;
        }
        for (UUID memberId : party.members()) {
            fire(consumers, memberId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnter(DungeonEnterEvent event) {
        if (plugin.isEnabled()) {
            fire(enterConsumers, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRoomClear(DungeonRoomClearEvent event) {
        if (plugin.isEnabled()) {
            fireParty(event.getInstanceId(), roomClearConsumers);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGateOpen(DungeonGateOpenEvent event) {
        if (plugin.isEnabled()) {
            fireParty(event.getInstanceId(), gateOpenConsumers);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onComplete(DungeonCompleteEvent event) {
        if (plugin.isEnabled()) {
            fireParty(event.getInstanceId(), completeConsumers);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFail(DungeonFailEvent event) {
        if (plugin.isEnabled()) {
            fireParty(event.getInstanceId(), failConsumers);
        }
    }

    public void shutdown() {
        enterConsumers.clear();
        roomClearConsumers.clear();
        gateOpenConsumers.clear();
        completeConsumers.clear();
        failConsumers.clear();
    }
}
