package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

abstract class LazyPluginBridge<T extends CustomBlockBridge> implements CustomBlockBridge {

    private final JavaPlugin owner;
    private final String pluginName;
    private final T noop;
    private volatile T delegate;
    private volatile boolean failed;

    LazyPluginBridge(JavaPlugin owner, String pluginName, T noop) {
        this.owner = owner;
        this.pluginName = pluginName;
        this.noop = noop;
    }

    protected abstract T createDelegate();

    @Override
    public boolean available() {
        return resolveDelegate().available();
    }

    @Override
    public boolean isCustomBlock(Block block) {
        return resolveDelegate().isCustomBlock(block);
    }

    @Override
    public String identifyBlock(Block block) {
        return resolveDelegate().identifyBlock(block);
    }

    @Override
    public boolean matches(Block block, String identifier) {
        return resolveDelegate().matches(block, identifier);
    }

    @Override
    public boolean setLit(Block block, boolean lit) {
        return resolveDelegate().setLit(block, lit);
    }

    @Override
    public boolean placeBlock(Block block, String identifier) {
        return resolveDelegate().placeBlock(block, identifier);
    }

    private T resolveDelegate() {
        T current = delegate;
        if (current != null) {
            return current;
        }
        if (failed || !isPluginEnabled()) {
            return noop;
        }
        synchronized (this) {
            current = delegate;
            if (current != null) {
                return current;
            }
            try {
                current = createDelegate();
                delegate = current;
                return current;
            } catch (LinkageError exception) {
                failed = true;
                if (owner != null) {
                    owner.getLogger().warning("Failed to initialize " + pluginName + " block API bridge: "
                            + Texts.toStringSafe(exception.getMessage()));
                }
                return noop;
            }
        }
    }

    private boolean isPluginEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }
}
