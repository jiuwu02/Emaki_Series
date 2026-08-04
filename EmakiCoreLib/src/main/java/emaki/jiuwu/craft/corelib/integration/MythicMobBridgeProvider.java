package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * MythicMobs 怪物元数据桥的门禁与延迟链接入口。
 *
 * <p>与 CoreLib 既有的方块桥 provider 同构：MythicMobs 未启用时返回 no-op，
 * 实例化失败只告警一次并永久落到 no-op，避免热路径反复重试与日志刷屏。
 */
public final class MythicMobBridgeProvider implements MythicMobBridge {

    private static final String PLUGIN_NAME = "MythicMobs";
    private static final MythicMobBridge NOOP = new NoopMythicMobBridge();

    private final JavaPlugin owner;
    private volatile MythicMobBridge delegate;
    private volatile boolean failed;

    public MythicMobBridgeProvider(JavaPlugin owner) {
        this.owner = owner;
    }

    @Override
    public boolean available() {
        return resolveDelegate().available();
    }

    @Override
    public MythicMobSnapshot snapshot(LivingEntity entity) {
        return resolveDelegate().snapshot(entity);
    }

    private MythicMobBridge resolveDelegate() {
        MythicMobBridge current = delegate;
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
                current = new MythicMobBridgeApi();
                delegate = current;
                return current;
            } catch (LinkageError exception) {
                failed = true;
                if (owner != null) {
                    owner.getLogger().warning("Failed to initialize MythicMobs mob API bridge: "
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
