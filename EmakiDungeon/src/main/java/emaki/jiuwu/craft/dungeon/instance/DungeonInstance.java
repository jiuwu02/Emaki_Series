package emaki.jiuwu.craft.dungeon.instance;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;

public final class DungeonInstance {

    private final String instanceId;
    private final String dungeonId;
    private final AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.CREATING);
    private final EventIngress ingress = new EventIngress();
    private final WorldCommandQueue commands;

    private volatile UUID partyId;
    private volatile String difficulty = "normal";
    private volatile String dropMode = "free";
    private volatile long startedAt;
    private volatile boolean test;
    private volatile InstanceLoop loop;
    private volatile emaki.jiuwu.craft.dungeon.flow.FlowRuntime flowRuntime;

    public DungeonInstance(String instanceId, String dungeonId, WorldCommandQueue commands) {
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
        this.commands = commands;
    }

    public String instanceId() {
        return instanceId;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public InstanceState state() {
        return state.get();
    }

    public UUID partyId() {
        return partyId;
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
    }

    public String difficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty == null ? "normal" : difficulty;
    }

    public String dropMode() {
        return dropMode;
    }

    public void setDropMode(String dropMode) {
        this.dropMode = dropMode == null || dropMode.isBlank() ? "free" : dropMode;
    }

    public long startedAt() {
        return startedAt;
    }

    public void markStarted() {
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
    }

    public boolean isTest() {
        return test;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public EventIngress ingress() {
        return ingress;
    }

    public emaki.jiuwu.craft.dungeon.flow.FlowRuntime flowRuntime() {
        return flowRuntime;
    }

    public void setFlowRuntime(emaki.jiuwu.craft.dungeon.flow.FlowRuntime flowRuntime) {
        this.flowRuntime = flowRuntime;
    }

    public WorldCommandQueue commands() {
        return commands;
    }

    public boolean transition(InstanceState from, InstanceState to) {
        return state.compareAndSet(from, to);
    }

    public void startLoop(Plugin plugin,
            ExecutionDispatcher dispatcher,
            Consumer<IngressEvent> eventConsumer,
            Runnable reconcile,
            Consumer<Throwable> crashHandler) {
        if (loop != null) {
            return;
        }
        loop = new InstanceLoop(instanceId, plugin, dispatcher, ingress, commands,
                eventConsumer, reconcile, crashHandler);
        loop.start();
    }

    public void closeLoop() {
        InstanceLoop current = loop;
        loop = null;
        if (current != null) {
            current.close();
        }
        ingress.close();
        commands.clear();
    }
}
