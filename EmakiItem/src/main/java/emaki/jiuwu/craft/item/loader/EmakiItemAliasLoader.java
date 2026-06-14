package emaki.jiuwu.craft.item.loader;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;

public final class EmakiItemAliasLoader {

    private final JavaPlugin plugin;
    private volatile Map<String, EmakiItemAlias> aliases = Map.of();

    public EmakiItemAliasLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
        File file = plugin.getDataFolder().toPath().resolve("id_aliases.yml").toFile();
        if (!file.exists()) {
            aliases = Map.of();
            return 0;
        }
        Map<String, EmakiItemAlias> loaded = new LinkedHashMap<>();
        YamlSection yaml = YamlFiles.load(file);
        Map<String, Object> root = ConfigNodes.entries(yaml.get("aliases"));
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String oldId = Texts.normalizeId(entry.getKey());
            Map<String, Object> data = ConfigNodes.entries(entry.getValue());
            String target = Texts.normalizeId(Texts.toStringSafe(data.get("target")));
            EmakiItemAlias alias = new EmakiItemAlias(
                    oldId,
                    target,
                    booleanValue(data.get("migrate_pdc"), true),
                    booleanValue(data.get("rewrite_display"), true),
                    Texts.toStringSafe(data.getOrDefault("expires_after", "never"))
            );
            if (!alias.valid()) {
                plugin.getLogger().warning("Invalid EmakiItem alias '" + oldId + "' in " + file.getPath() + ", skipped.");
                continue;
            }
            loaded.put(alias.oldId(), alias);
        }
        aliases = new ConcurrentHashMap<>(loaded);
        return aliases.size();
    }

    public EmakiItemAlias get(String oldId) {
        return aliases.get(Texts.normalizeId(oldId));
    }

    public Map<String, EmakiItemAlias> all() {
        return Map.copyOf(aliases);
    }

    public void put(String oldId, String targetId) {
        Map<String, EmakiItemAlias> next = new LinkedHashMap<>(aliases);
        EmakiItemAlias alias = new EmakiItemAlias(oldId, targetId, true, true, "never");
        if (alias.valid()) {
            next.put(alias.oldId(), alias);
            save(next);
            aliases = new ConcurrentHashMap<>(next);
        }
    }

    public boolean remove(String oldId) {
        Map<String, EmakiItemAlias> next = new LinkedHashMap<>(aliases);
        EmakiItemAlias removed = next.remove(Texts.normalizeId(oldId));
        if (removed == null) {
            return false;
        }
        save(next);
        aliases = new ConcurrentHashMap<>(next);
        return true;
    }

    private void save(Map<String, EmakiItemAlias> source) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> aliasMap = new LinkedHashMap<>();
        for (EmakiItemAlias alias : source.values()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("target", alias.targetId());
            data.put("migrate_pdc", alias.migratePdc());
            data.put("rewrite_display", alias.rewriteDisplay());
            data.put("expires_after", Texts.isBlank(alias.expiresAfter()) ? "never" : alias.expiresAfter());
            aliasMap.put(alias.oldId(), data);
        }
        root.put("aliases", aliasMap);
        try {
            YamlFiles.save(plugin.getDataFolder().toPath().resolve("id_aliases.yml").toFile(), root);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save id_aliases.yml: " + e.getMessage());
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        String text = Texts.toStringSafe(value).trim();
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }
}
