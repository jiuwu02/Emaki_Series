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


    public boolean shouldLog(String module, UUID player) {
        if (!globalEnabled) {
            return false;
        }
        String normalizedModule = Texts.lower(module);
        if (!enabledModules.isEmpty() && !enabledModules.contains(normalizedModule)) {
            return false;
        }
        return trackedPlayers.isEmpty() || player == null || trackedPlayers.contains(player);
    }

    public boolean shouldLog(String module, Player player) {
        return shouldLog(module, player == null ? null : player.getUniqueId());
    }


    public void log(String module, UUID player, String langKey, Map<String, ?> replacements) {
        if (!shouldLog(module, player)) {
            return;
        }
        String template = languageLoader.getMessage("debug." + langKey);
        String message = replacements == null || replacements.isEmpty()
                ? template
                : Texts.formatTemplate(template, replacements);
        logger.info("[DEBUG][" + Texts.lower(module) + "] " + message);
    }

    public void logRaw(String module, UUID player, String message) {
        if (!shouldLog(module, player)) {
            return;
        }
        logger.info("[DEBUG][" + Texts.lower(module) + "] " + Texts.toStringSafe(message));
    }

    public void logRaw(String module, Player player, String message) {
        logRaw(module, player == null ? null : player.getUniqueId(), message);
    }

    public void log(String module, UUID player, String langKey) {
        log(module, player, langKey, Map.of());
    }

    public void log(String module, Player player, String langKey, Map<String, ?> replacements) {
        log(module, player == null ? null : player.getUniqueId(), langKey, replacements);
    }

    public void log(String module, Player player, String langKey) {
        log(module, player == null ? null : player.getUniqueId(), langKey, Map.of());
    }


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


    public void enableAll() {
        trackedPlayers.clear();
        enabledModules.clear();
        globalEnabled = true;
    }

    public void disableAll() {
        trackedPlayers.clear();
        enabledModules.clear();
        globalEnabled = false;
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    private void refreshGlobalState() {
        globalEnabled = !trackedPlayers.isEmpty() || !enabledModules.isEmpty();
    }
}
