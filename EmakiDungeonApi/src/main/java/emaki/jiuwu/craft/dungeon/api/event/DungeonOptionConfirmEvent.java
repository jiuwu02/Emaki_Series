package emaki.jiuwu.craft.dungeon.api.event;

import java.util.Set;

import org.bukkit.event.HandlerList;

public class DungeonOptionConfirmEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String optionId;
    private final Set<String> selected;

    public DungeonOptionConfirmEvent(String instanceId, String dungeonId, String optionId, Set<String> selected) {
        super(instanceId, dungeonId);
        this.optionId = optionId == null ? "" : optionId;
        this.selected = selected == null ? Set.of() : Set.copyOf(selected);
    }

    public String getOptionId() {
        return optionId;
    }

    public Set<String> getSelected() {
        return selected;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
