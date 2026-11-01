package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.editor.EditorDocument;
import emaki.jiuwu.craft.dungeon.flow.FlowPoint;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;

public final class EditorSession {

    private final UUID playerId;
    private final String dungeonId;
    private final String editWorldName;
    private final WorldCommandQueue queue;
    private final List<String> stagedMarks = new ArrayList<>();
    private final Map<String, Long> regionSnapshot = new LinkedHashMap<>();
    private Location origin;
    private World editWorld;
    private FlowPoint pos1;
    private FlowPoint pos2;
    private EditorDocument configDoc;
    private EditorDocument flowDoc;
    private TaskToken pump;
    private boolean dirty;
    private boolean ready;
    private volatile int inFlight;

    public EditorSession(UUID playerId, String dungeonId, String editWorldName, WorldCommandQueue queue) {
        this.playerId = playerId;
        this.dungeonId = dungeonId;
        this.editWorldName = editWorldName;
        this.queue = queue;
    }

    public UUID playerId() {
        return playerId;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public String editWorldName() {
        return editWorldName;
    }

    public WorldCommandQueue queue() {
        return queue;
    }

    public List<String> stagedMarks() {
        return stagedMarks;
    }

    public Map<String, Long> regionSnapshot() {
        return regionSnapshot;
    }

    public Location origin() {
        return origin;
    }

    public void origin(Location origin) {
        this.origin = origin;
    }

    public World editWorld() {
        return editWorld;
    }

    public void editWorld(World editWorld) {
        this.editWorld = editWorld;
    }

    public FlowPoint pos1() {
        return pos1;
    }

    public void pos1(FlowPoint pos1) {
        this.pos1 = pos1;
    }

    public FlowPoint pos2() {
        return pos2;
    }

    public void pos2(FlowPoint pos2) {
        this.pos2 = pos2;
    }

    public EditorDocument configDoc() {
        return configDoc;
    }

    public void configDoc(EditorDocument configDoc) {
        this.configDoc = configDoc;
    }

    public EditorDocument flowDoc() {
        return flowDoc;
    }

    public void flowDoc(EditorDocument flowDoc) {
        this.flowDoc = flowDoc;
    }

    public TaskToken pump() {
        return pump;
    }

    public void pump(TaskToken pump) {
        this.pump = pump;
    }

    public boolean dirty() {
        return dirty;
    }

    public void dirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean ready() {
        return ready;
    }

    public void ready(boolean ready) {
        this.ready = ready;
    }

    public int inFlight() {
        return inFlight;
    }

    public void inFlight(int inFlight) {
        this.inFlight = inFlight;
    }

    public void inFlightAdd(int delta) {
        inFlight += delta;
    }
}
