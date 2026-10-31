package emaki.jiuwu.craft.dungeon.script;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.dungeon.flow.FlowControllers;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.potion.PotionEffectType;

public final class ScriptHostObject {

    private final DungeonScriptSession session;
    private final FlowRuntime runtime;
    private final FlowControllers controllers;
    private final DungeonInstance instance;

    ScriptHostObject(DungeonScriptSession session,
            FlowRuntime runtime,
            FlowControllers controllers,
            DungeonInstance instance) {
        this.session = session;
        this.runtime = runtime;
        this.controllers = controllers;
        this.instance = instance;
    }

    @HostAccess.Export
    public void __registerHandler(String event, Value handler) {
        session.registerHandler(event, handler);
    }

    @HostAccess.Export
    public void __reportError(String trigger, String detail) {
        session.onScriptError(trigger, detail);
    }

    @HostAccess.Export
    public void takeover() {
        runtime.setScriptTakeover(true);
        session.onTakeover();
    }

    @HostAccess.Export
    public void releaseTakeover() {
        runtime.setScriptTakeover(false);
    }

    @HostAccess.Export
    public void enterRoom(String roomId) {
        runtime.scriptEnterRoom(roomId);
    }

    @HostAccess.Export
    public void hint(String text, String channel) {
        runtime.scriptHint(text, channel);
    }

    @HostAccess.Export
    public boolean openGate(String gateId) {
        return controllers != null && controllers.openGate(runtime, gateId, session.anyPlayer());
    }

    @HostAccess.Export
    public boolean spawnWave(String spawnId, int waveIndex) {
        return controllers != null && controllers.spawnWave(runtime, spawnId, waveIndex, session.anyPlayer());
    }

    @HostAccess.Export
    public void triggerOption(String optionId) {
        if (controllers != null) {
            controllers.handleOptionTrigger(runtime, optionId, session.anyPlayer());
        }
    }

    @HostAccess.Export
    public void complete(String cause) {
        if (controllers != null) {
            controllers.triggerComplete(runtime,
                    cause == null || cause.isBlank() ? "script" : cause);
        }
    }

    @HostAccess.Export
    public void fail(String reason) {
        if (controllers != null) {
            controllers.triggerFail(runtime,
                    reason == null || reason.isBlank() ? "scripted" : reason);
        }
    }

    @HostAccess.Export
    public void ending(String optionCase, String endingId) {
        if (controllers != null) {
            controllers.triggerEnding(runtime, optionCase, endingId);
        }
    }

    @HostAccess.Export
    public void giveTempItem(String itemId, int amount) {
        if (controllers != null) {
            controllers.giveTempItem(runtime, itemId, amount);
        }
    }

    @HostAccess.Export
    public void setFact(String key, Object value) {
        runtime.setFact(key, value);
    }

    @HostAccess.Export
    public String fact(String key) {
        Object value = runtime.fact(key);
        return value == null ? null : String.valueOf(value);
    }

    @HostAccess.Export
    public int factInt(String key, int fallback) {
        return runtime.factInt(key, fallback);
    }

    @HostAccess.Export
    public boolean factFlag(String key) {
        return runtime.factFlag(key);
    }

    @HostAccess.Export
    public int playerCount() {
        return session.playerCount();
    }

    @HostAccess.Export
    public String difficulty() {
        return instance.difficulty();
    }

    @HostAccess.Export
    public String instanceId() {
        return instance.instanceId();
    }

    @HostAccess.Export
    public String dungeonId() {
        return instance.dungeonId();
    }

    @HostAccess.Export
    public void registerEntry(String id, String effect, String description) {
        session.entries().register(new EntryRegistry.Entry(id, effect, description));
        session.onEntryRegistered(id);
        if (effect != null && !effect.isBlank()) {
            applyEntryEffect(effect);
        }
    }

    @HostAccess.Export
    public boolean removeEntry(String id) {
        return session.entries().remove(id);
    }

    @HostAccess.Export
    public boolean hasEntry(String id) {
        return session.entries().has(id);
    }

    @HostAccess.Export
    public String[] entryIds() {
        return session.entries().ids();
    }

    @HostAccess.Export
    public String entryEffect(String id) {
        EntryRegistry.Entry entry = session.entries().get(id);
        return entry == null ? null : entry.effect();
    }

    private void applyEntryEffect(String spec) {
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.LOCATION;
            }

            @Override
            public String worldName() {
                return instance.instanceId();
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                List<UUID> audience = new ArrayList<>();
                for (Player member : world.getPlayers()) {
                    audience.add(member.getUniqueId());
                }
                for (UUID memberId : audience) {
                    submitEntryEffect(memberId, spec);
                }
            }
        });
    }

    private void submitEntryEffect(UUID playerId, String spec) {
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.ENTITY;
            }

            @Override
            public UUID entityId() {
                return playerId;
            }

            @Override
            public void execute() {
                Player member = Bukkit.getPlayer(playerId);
                if (member != null) {
                    applySpec(member, spec);
                }
            }
        });
    }

    private void applySpec(Player player, String spec) {
        String[] parts = spec.split(":");
        if (parts.length == 0 || parts[0].isBlank()) {
            return;
        }
        try {
            PotionEffectType type =
                    PotionEffectType.getByName(parts[0].toUpperCase(Locale.ROOT));
            if (type == null) {
                return;
            }
            int amplifier = parts.length > 1 ? parseSafe(parts[1], 0) : 0;
            int duration = parts.length > 2 ? parseSafe(parts[2], 100) : 100;
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    type, duration, amplifier, true, true));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static int parseSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
