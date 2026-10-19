package emaki.jiuwu.craft.dungeon.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Party {

    private final UUID id;
    private volatile UUID leaderId;
    private final List<UUID> members = new CopyOnWriteArrayList<>();

    public Party(UUID id, UUID leaderId) {
        this.id = id;
        this.leaderId = leaderId;
        this.members.add(leaderId);
    }

    public UUID id() {
        return id;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    public List<String> memberNames() {
        List<String> names = new ArrayList<>(members.size());
        for (UUID memberId : members) {
            names.add(String.valueOf(memberId));
        }
        return names;
    }

    public boolean contains(UUID playerId) {
        return playerId != null && members.contains(playerId);
    }

    public boolean isLeader(UUID playerId) {
        return playerId != null && playerId.equals(leaderId);
    }

    public int size() {
        return members.size();
    }

    public boolean add(UUID playerId) {
        return playerId != null && !members.contains(playerId) && members.add(playerId);
    }

    public boolean remove(UUID playerId) {
        return playerId != null && members.remove(playerId);
    }

    public UUID transferLeadership(UUID leavingId) {
        for (UUID memberId : members) {
            if (!memberId.equals(leavingId)) {
                leaderId = memberId;
                return memberId;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
