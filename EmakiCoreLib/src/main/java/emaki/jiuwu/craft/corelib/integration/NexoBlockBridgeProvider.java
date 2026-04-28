package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class NexoBlockBridgeProvider implements CustomBlockBridge {

    private static final String PLUGIN_NAME = "Nexo";
    private static final CustomBlockBridge NOOP = new NoopCustomBlockBridge();

    private final JavaPlugin owner;
    private volatile CustomBlockBridge delegate;
    private volatile boolean failed;

    public NexoBlockBridgeProvider(JavaPlugin owner) {
        this.owner = owner;
    }

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

    private CustomBlockBridge resolveDelegate() {
        CustomBlockBridge current = delegate;
        if (current != null) {
            return current;
        }
        if (failed || !isPluginEnabled()) {
            return NOOP;
        }
        synchronized (this) {
            current = delegate;
            if (current != null) {
                return current;
            }
            try {
                current = new NexoBlockBridgeApi();
                delegate = current;
                return current;
            } catch (LinkageError exception) {
                failed = true;
                if (owner != null) {
                    owner.getLogger().warning("Failed to initialize Nexo block API bridge: "
                            + Texts.toStringSafe(exception.getMessage()));
                }
                return NOOP;
            }
        }
    }

    private boolean isPluginEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }
}
