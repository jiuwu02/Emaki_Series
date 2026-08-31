package emaki.jiuwu.craft.corelib.placeholder;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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

    @Override
    public boolean persist() {
        return true;
    }
}
