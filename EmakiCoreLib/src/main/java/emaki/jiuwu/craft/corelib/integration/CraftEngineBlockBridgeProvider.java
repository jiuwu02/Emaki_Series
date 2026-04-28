package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class CraftEngineBlockBridgeProvider implements CraftEngineBlockBridge {

    private static final String PLUGIN_NAME = "CraftEngine";
    private static final CraftEngineBlockBridge NOOP = new NoopCraftEngineBlockBridge();

    private final JavaPlugin owner;
    private volatile CraftEngineBlockBridge delegate;
    private volatile boolean failed;

    public CraftEngineBlockBridgeProvider(JavaPlugin owner) {
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

    private CraftEngineBlockBridge resolveDelegate() {
        CraftEngineBlockBridge current = delegate;
        if (current != null) {
            return current;
        }
        if (failed || !isCraftEngineEnabled()) {
            return NOOP;
        }
        synchronized (this) {
            current = delegate;
            if (current != null) {
                return current;
            }
            try {
                current = new CraftEngineBlockBridgeApi();
                delegate = current;
                return current;
            } catch (LinkageError exception) {
                failed = true;
                if (owner != null) {
                    owner.getLogger().warning("Failed to initialize CraftEngine block API bridge: "
                            + Texts.toStringSafe(exception.getMessage()));
                }
                return NOOP;
            }
        }
    }

    private boolean isCraftEngineEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    private static final class NoopCraftEngineBlockBridge implements CraftEngineBlockBridge {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean isCustomBlock(Block block) {
            return false;
        }

        @Override
        public String identifyBlock(Block block) {
            return "";
        }

        @Override
        public boolean matches(Block block, String identifier) {
            return false;
        }
    }
}
