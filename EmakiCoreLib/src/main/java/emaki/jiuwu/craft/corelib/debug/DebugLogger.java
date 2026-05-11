package emaki.jiuwu.craft.corelib.debug;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 统一 debug 日志服务。
 * <p>
 * 每个插件持有一个实例，管理该插件下的模块和玩家追踪。
 * 所有 debug 消息模板从语言文件读取，不硬编码任何文本。
 * </p>
 * <p>
 * 判断逻辑：
 * <ul>
 *   <li>至少有一个玩家或模块被追踪时才激活（完全空 = 关闭）</li>
 *   <li>trackedPlayers 为空 = 不限玩家；非空则 player 必须在集合中</li>
 *   <li>enabledModules 为空 = 不限模块；非空则 module 必须在集合中</li>
 * </ul>
 */
public final class DebugLogger {

    private final Logger logger;
    private final LanguageLoader languageLoader;
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> enabledModules = ConcurrentHashMap.newKeySet();
    private volatile boolean globalEnabled;

    public DebugLogger(Logger logger, LanguageLoader languageLoader) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.languageLoader = Objects.requireNonNull(languageLoader, "languageLoader");
    }

    // ========== 判断方法 ==========

    /**
     * 判断是否应该输出指定模块和玩家的 debug 日志。
     */
    public boolean shouldLog(String module, UUID player) {
        if (!globalEnabled) {
            return false;
        }
        if (!enabledModules.isEmpty() && !enabledModules.contains(module)) {
            return false;
        }
        return trackedPlayers.isEmpty() || player == null || trackedPlayers.contains(player);
    }

    /**
     * 便捷方法：通过 Player 对象判断。
     */
    public boolean shouldLog(String module, Player player) {
        return shouldLog(module, player == null ? null : player.getUniqueId());
    }

    // ========== 日志输出方法 ==========

    /**
     * 通过语言文件 key 输出 debug 日志。
     *
     * @param module       模块名
     * @param player       相关玩家 UUID（可为 null）
     * @param langKey      语言文件 key（自动加 "debug." 前缀）
     * @param replacements 占位符替换
     */
    public void log(String module, UUID player, String langKey, Map<String, ?> replacements) {
        if (!shouldLog(module, player)) {
            return;
        }
        String template = languageLoader.getMessage("debug." + langKey);
        String message = replacements == null || replacements.isEmpty()
                ? template
                : Texts.formatTemplate(template, replacements);
        logger.info("[DEBUG][" + module + "] " + message);
    }

    /**
     * 通过语言文件 key 输出 debug 日志（无占位符）。
     */
    public void log(String module, UUID player, String langKey) {
        log(module, player, langKey, Map.of());
    }

    /**
     * 便捷方法：通过 Player 对象输出。
     */
    public void log(String module, Player player, String langKey, Map<String, ?> replacements) {
        log(module, player == null ? null : player.getUniqueId(), langKey, replacements);
    }

    /**
     * 便捷方法：通过 Player 对象输出（无占位符）。
     */
    public void log(String module, Player player, String langKey) {
        log(module, player == null ? null : player.getUniqueId(), langKey, Map.of());
    }

    // ========== 玩家追踪管理 ==========

    public boolean addPlayer(UUID player) {
        if (player == null) {
            return false;
        }
        boolean added = trackedPlayers.add(player);
        refreshGlobalState();
        return added;
    }

    public boolean removePlayer(UUID player) {
        if (player == null) {
            return false;
        }
        boolean removed = trackedPlayers.remove(player);
        refreshGlobalState();
        return removed;
    }

    public boolean togglePlayer(UUID player) {
        if (player == null) {
            return false;
        }
        if (trackedPlayers.remove(player)) {
            refreshGlobalState();
            return false;
        }
        trackedPlayers.add(player);
        refreshGlobalState();
        return true;
    }

    public Set<UUID> trackedPlayers() {
        return Collections.unmodifiableSet(trackedPlayers);
    }

    // ========== 模块追踪管理 ==========

    public boolean enableModule(String module) {
        if (Texts.isBlank(module)) {
            return false;
        }
        boolean added = enabledModules.add(module.toLowerCase());
        refreshGlobalState();
        return added;
    }

    public boolean disableModule(String module) {
        if (Texts.isBlank(module)) {
            return false;
        }
        boolean removed = enabledModules.remove(module.toLowerCase());
        refreshGlobalState();
        return removed;
    }

    public boolean toggleModule(String module) {
        if (Texts.isBlank(module)) {
            return false;
        }
        String normalized = module.toLowerCase();
        if (enabledModules.remove(normalized)) {
            refreshGlobalState();
            return false;
        }
        enabledModules.add(normalized);
        refreshGlobalState();
        return true;
    }

    public Set<String> enabledModules() {
        return Collections.unmodifiableSet(enabledModules);
    }

    // ========== 全局控制 ==========

    /**
     * 开启全局追踪（所有玩家 + 所有模块）。
     */
    public void enableAll() {
        trackedPlayers.clear();
        enabledModules.clear();
        globalEnabled = true;
    }

    /**
     * 关闭所有追踪。
     */
    public void disableAll() {
        trackedPlayers.clear();
        enabledModules.clear();
        globalEnabled = false;
    }

    /**
     * 是否有任何追踪活跃。
     */
    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    private void refreshGlobalState() {
        globalEnabled = !trackedPlayers.isEmpty() || !enabledModules.isEmpty();
    }
}
