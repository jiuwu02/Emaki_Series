package emaki.jiuwu.craft.dungeon.api.event;

import java.util.Set;

import org.bukkit.event.HandlerList;

public class DungeonSealPathEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String optionId;
    private final Set<String> sealedCases;

    public DungeonSealPathEvent(String instanceId, String dungeonId, String optionId, Set<String> sealedCases) {
        super(instanceId, dungeonId);
        this.optionId = optionId == null ? "" : optionId;
        this.sealedCases = sealedCases == null ? Set.of() : Set.copyOf(sealedCases);
    }

    public String getOptionId() {
        return optionId;
    }

    public Set<String> getSealedCases() {
        return sealedCases;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
