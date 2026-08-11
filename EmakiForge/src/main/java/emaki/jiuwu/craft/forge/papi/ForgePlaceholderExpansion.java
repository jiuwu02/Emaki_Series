package emaki.jiuwu.craft.forge.papi;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.model.PlayerData;

public final class ForgePlaceholderExpansion extends AbstractEmakiPlaceholderExpansion {

    private final EmakiForgePlugin plugin;
    private final PlayerDataStore playerDataStore;

    public ForgePlaceholderExpansion(EmakiForgePlugin plugin, PlayerDataStore playerDataStore) {
        super(plugin);
        this.plugin = plugin;
        this.playerDataStore = playerDataStore;
    }

    @Override
    public String getIdentifier() {
        return "emakiforge";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);

        if ("recipe_count".equals(normalized)) {
            return String.valueOf(plugin.recipeLoader().all().size());
        }

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
        if ("last_crafted".equals(normalized)) {
            return resolveLastCrafted(player);
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

    private String resolveLastCrafted(Player player) {
        PlayerData data = playerDataStore.get(player.getUniqueId());
        if (data == null) {
            return "";
        }
        String lastRecipeId = "";
        String latestTimestamp = "";
        for (Map.Entry<String, Object> entry : data.toMap().entrySet()) {
            if (!"recipes".equals(entry.getKey()) || !(entry.getValue() instanceof Map<?, ?> recipes)) {
                continue;
            }
            for (Map.Entry<?, ?> recipeEntry : recipes.entrySet()) {
                if (!(recipeEntry.getValue() instanceof Map<?, ?> historyMap)) {
                    continue;
                }
                Object lastAt = historyMap.get("last_crafted_at");
                if (lastAt == null) {
                    continue;
                }
                String timestamp = String.valueOf(lastAt);
                if (timestamp.compareTo(latestTimestamp) > 0) {
                    latestTimestamp = timestamp;
                    lastRecipeId = String.valueOf(recipeEntry.getKey());
                }
            }
        }
        return lastRecipeId;
    }
}
