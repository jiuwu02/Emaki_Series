package emaki.jiuwu.craft.skills.api;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Supplies skills unlocked by an external plugin for a live player. */
public interface SkillSourceProvider {

    /** Stable id unique within the owning plugin. */
    @NotNull
    String id();

    /** Lower values are evaluated first. */
    default int priority() {
        return 100;
    }

    /**
     * Collects the source's current skill entries.
     *
     * <p>Invoked synchronously on the player's owner thread. Implementations must be fast and must not
     * access Bukkit state owned by another entity or region.
     */
    @NotNull
    Collection<SkillSourceEntry> collect(@NotNull Player player);
}
