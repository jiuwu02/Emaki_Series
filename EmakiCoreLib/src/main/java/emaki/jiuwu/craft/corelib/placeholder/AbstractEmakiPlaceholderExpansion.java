package emaki.jiuwu.craft.corelib.placeholder;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * Shared base for Emaki PlaceholderAPI expansions.
 *
 * <p>Centralises the boilerplate that every module repeated: version reporting via the
 * non-deprecated {@code getPluginMeta()}, a default author, and {@code persist() == true}
 * so an expansion survives a PAPI reload.
 *
 * <p>Subclasses supply {@link #getIdentifier()} and {@code onPlaceholderRequest(...)}.
 * {@link #getAuthor()} is overridable because it is user-visible in {@code /papi info}.
 */
public abstract class AbstractEmakiPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    protected AbstractEmakiPlaceholderExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    protected final JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Emaki";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * Keeps the expansion registered across a PlaceholderAPI reload, matching the
     * behaviour every Emaki module already relied on.
     */
    @Override
    public boolean persist() {
        return true;
    }
}
