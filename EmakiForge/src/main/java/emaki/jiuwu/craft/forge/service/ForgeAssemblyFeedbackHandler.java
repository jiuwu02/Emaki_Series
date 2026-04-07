package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.assembly.AssemblyFeedbackHandler;
import emaki.jiuwu.craft.corelib.assembly.EmakiPresentationEntry;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertConfig;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ForgeAssemblyFeedbackHandler implements AssemblyFeedbackHandler {

    private final EmakiForgePlugin plugin;

    public ForgeAssemblyFeedbackHandler(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onLoreSearchNotFound(UUID playerId, EmakiPresentationEntry entry, SearchInsertConfig config) {
        Map<String, Object> replacements = replacements(entry, config, "");
        warn("console.lore_search_not_found", replacements);
        send(playerId, "forge.error.lore_search_not_found", replacements);
    }

    @Override
    public void onLoreInvalidRegex(UUID playerId,
            EmakiPresentationEntry entry,
            SearchInsertConfig config,
            String error) {
        Map<String, Object> replacements = replacements(entry, config, error);
        warn("console.lore_invalid_regex", replacements);
        send(playerId, "forge.error.lore_invalid_regex", replacements);
    }

    @Override
    public void onLoreInvalidConfig(UUID playerId, EmakiPresentationEntry entry, String error) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("source", entry == null || Texts.isBlank(entry.sourceNamespace()) ? "unknown" : entry.sourceNamespace());
        replacements.put("action", entry == null ? "lore_search_insert" : Texts.toStringSafe(entry.entryType()));
        replacements.put("pattern", "");
        replacements.put("error", Texts.toStringSafe(error));
        warn("console.lore_invalid_search_insert_config", replacements);
    }

    private Map<String, Object> replacements(EmakiPresentationEntry entry, SearchInsertConfig config, String error) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("source", entry == null || Texts.isBlank(entry.sourceNamespace()) ? "unknown" : entry.sourceNamespace());
        replacements.put("action", config == null ? "lore_search_insert" : Texts.toStringSafe(config.actionType()));
        replacements.put("pattern", config == null ? "" : Texts.toStringSafe(config.searchPattern()));
        replacements.put(
                "mode",
                config == null || config.searchMode() == null ? "" : config.searchMode().name().toLowerCase(Locale.ROOT)
        );
        replacements.put("error", Texts.toStringSafe(error));
        return replacements;
    }

    private void warn(String key, Map<String, ?> replacements) {
        if (plugin == null || plugin.messageService() == null) {
            return;
        }
        plugin.messageService().warning(key, replacements);
    }

    private void send(UUID playerId, String key, Map<String, ?> replacements) {
        if (plugin == null || plugin.messageService() == null || playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.messageService().send(player, key, replacements);
    }
}
