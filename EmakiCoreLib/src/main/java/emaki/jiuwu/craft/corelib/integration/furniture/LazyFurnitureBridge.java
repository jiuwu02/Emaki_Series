package emaki.jiuwu.craft.corelib.integration.furniture;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

abstract class LazyFurnitureBridge implements FurnitureBridge {

    private final JavaPlugin owner;
    private final String pluginName;
    private volatile FurnitureBridge delegate;
    private volatile boolean failed;

    LazyFurnitureBridge(JavaPlugin owner, String pluginName) {
        this.owner = owner;
        this.pluginName = pluginName;
    }

    protected abstract FurnitureBridge createDelegate();

    @Override
    public boolean available() {
        if (failed) {
            return false;
        }

        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    @Override
    public String identify(Entity entity) {
        FurnitureBridge current = resolve();
        return current == null ? null : current.identify(entity);
    }

    @Override
    public String identifyAt(Block block) {
        FurnitureBridge current = resolve();
        return current == null ? null : current.identifyAt(block);
    }

    private FurnitureBridge resolve() {
        if (failed || !isPluginEnabled()) {
            return null;
        }
        FurnitureBridge current = delegate;
        if (current != null) {
            return current;
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
                    owner.getLogger().warning("[integration] 初始化 " + pluginName + " 家具 API 桥接失败: "
                            + Texts.toStringSafe(exception.getMessage()));
                }
                return null;
            }
        }
    }

    private boolean isPluginEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }
}
