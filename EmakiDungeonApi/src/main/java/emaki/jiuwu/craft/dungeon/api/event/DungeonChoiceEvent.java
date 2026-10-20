package emaki.jiuwu.craft.dungeon.api.event;

import java.util.Set;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class DungeonChoiceEvent extends DungeonEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String optionId;
    private final String selectMode;
    private final Set<String> selected;
    private boolean cancelled;

    public DungeonChoiceEvent(String instanceId, String dungeonId, String optionId,
            String selectMode, Set<String> selected) {
        super(instanceId, dungeonId);
        this.optionId = optionId == null ? "" : optionId;
        this.selectMode = selectMode == null ? "single" : selectMode;
        this.selected = selected == null ? Set.of() : Set.copyOf(selected);
    }

    public String getOptionId() {
        return optionId;
    }

    public String getSelectMode() {
        return selectMode;
    }

    public Set<String> getSelected() {
        return selected;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
