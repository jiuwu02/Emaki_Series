package emaki.jiuwu.craft.forge.papi;

import java.util.Locale;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.model.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class ForgePlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiForgePlugin plugin;
    private final PlayerDataStore playerDataStore;

    public ForgePlaceholderExpansion(EmakiForgePlugin plugin, PlayerDataStore playerDataStore) {
        this.plugin = plugin;
        this.playerDataStore = playerDataStore;
    }

    @Override
    public String getIdentifier() {
        return "emakiforge";
    }

    @Override
    public String getAuthor() {
        return "Emaki";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("craft_count_")) {
            String recipeId = normalized.substring("craft_count_".length());
            if (Texts.isBlank(recipeId)) {
                return "0";
            }
            return String.valueOf(playerDataStore.craftCount(player.getUniqueId(), recipeId));
        }
        if (normalized.startsWith("has_crafted_")) {
            String recipeId = normalized.substring("has_crafted_".length());
            if (Texts.isBlank(recipeId)) {
                return "false";
            }
            return String.valueOf(playerDataStore.hasCrafted(player.getUniqueId(), recipeId));
        }
        if ("total_crafts".equals(normalized)) {
            PlayerData data = playerDataStore.get(player.getUniqueId());
            return String.valueOf(data.totalCraftCount());
        }
        if (normalized.startsWith("guarantee_")) {
            String key = normalized.substring("guarantee_".length());
            if (Texts.isBlank(key)) {
                return "0";
            }
            return String.valueOf(playerDataStore.guaranteeCounter(player.getUniqueId(), key));
        }
        return "";
    }
}
