package emaki.jiuwu.craft.dungeon.challenge;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.boss.BossBar;

public final class ChallengeSession {

    private final UUID partyId;
    private final String dungeonId;
    private final UUID leaderId;
    private volatile ChallengePhase phase;
    private volatile String difficulty = "";
    private volatile String dropMode = "";
    private final Set<UUID> ready = ConcurrentHashMap.newKeySet();
    private volatile long windowDeadline;
    private volatile long waitDeadline;
    private volatile BossBar waitBar;

    public ChallengeSession(UUID partyId, String dungeonId, UUID leaderId) {
        this.partyId = partyId;
        this.dungeonId = dungeonId;
        this.leaderId = leaderId;
        this.phase = ChallengePhase.SELECT_DIFFICULTY;
    }

    public UUID partyId() {
        return partyId;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public ChallengePhase phase() {
        return phase;
    }

    public void setPhase(ChallengePhase phase) {
        this.phase = phase;
    }

    public String difficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty == null ? "" : difficulty;
    }

    public String dropMode() {
        return dropMode;
    }

    public void setDropMode(String dropMode) {
        this.dropMode = dropMode == null ? "" : dropMode;
    }

    public Set<UUID> ready() {
        return ready;
    }

    public long windowDeadline() {
        return windowDeadline;
    }

    public void setWindowDeadline(long windowDeadline) {
        this.windowDeadline = windowDeadline;
    }

    public long waitDeadline() {
        return waitDeadline;
    }

    public void setWaitDeadline(long waitDeadline) {
        this.waitDeadline = waitDeadline;
    }

    public BossBar waitBar() {
        return waitBar;
    }

    public void setWaitBar(BossBar waitBar) {
        this.waitBar = waitBar;
    }
}
