package emaki.jiuwu.craft.level.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.api.LevelTypeView;

public final class ScriptLevelModuleApi {

    @HostAccess.Export
    public boolean available() {
        return EmakiLevelApi.available();
    }

    @HostAccess.Export
    public java.util.List<String> typeIds() {
        return EmakiLevelApi.types().stream()
                .map(LevelTypeView::id)
                .sorted()
                .toList();
    }

    @HostAccess.Export
    public Map<String, Object> type(String typeId) {
        return EmakiLevelApi.type(typeId)
                .map(this::typeToMap)
                .orElseGet(Map::of);
    }

    @HostAccess.Export
    public int level(String playerUuid, String typeId) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0 : EmakiLevelApi.getLevel(uuid, typeId);
    }

    @HostAccess.Export
    public double exp(String playerUuid, String typeId) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0D : EmakiLevelApi.getExp(uuid, typeId);
    }

    @HostAccess.Export
    public double totalExp(String playerUuid, String typeId) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0D : EmakiLevelApi.getTotalExp(uuid, typeId);
    }

    @HostAccess.Export
    public double requiredExp(String playerUuid, String typeId, int targetLevel) {
        UUID uuid = uuid(playerUuid);
        return uuid == null ? 0D : EmakiLevelApi.getRequiredExp(uuid, typeId, targetLevel);
    }

    private Map<String, Object> typeToMap(LevelTypeView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", view.id());
        map.put("displayName", view.displayName());
        map.put("description", view.description());
        map.put("primary", view.primary());
        map.put("enabled", view.enabled());
        map.put("startLevel", view.startLevel());
        map.put("maxLevel", view.maxLevel());
        map.put("autoUpgrade", view.autoUpgrade());
        map.put("manualUpgrade", view.manualUpgrade());
        map.put("attributes", view.attributes());
        return map;
    }

    private UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
