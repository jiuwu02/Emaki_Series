package emaki.jiuwu.craft.skills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a skill effect succeeded and its costs/cooldowns were committed. */
public final class SkillPostCastEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String skillId;
    private final String triggerId;

    public SkillPostCastEvent(@NotNull Player player, @NotNull String skillId, @NotNull String triggerId) {
        this.player = player;
        this.skillId = skillId;
        this.triggerId = triggerId;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull String getSkillId() {
        return skillId;
    }

    public @NotNull String getTriggerId() {
        return triggerId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
