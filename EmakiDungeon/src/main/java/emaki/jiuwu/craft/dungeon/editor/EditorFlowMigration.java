package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.dungeon.flow.FlowLoader;

final class EditorFlowMigration {

    private EditorFlowMigration() {
    }

    static boolean normalize(YamlTextDocument view) {
        boolean changed = false;
        for (Map.Entry<String, String> entry : FlowLoader.legacySections().entrySet()) {
            changed |= mergeSection(view, entry.getKey(), entry.getValue());
        }
        changed |= migrateBossPoints(view);
        changed |= migrateTipTypes(view);
        return changed;
    }

    private static boolean mergeSection(YamlTextDocument view, String legacy, String current) {
        Object raw = view.value(legacy);
        if (raw == null) {
            if (view.has(legacy)) {
                view.remove(legacy);
                return true;
            }
            return false;
        }
        if (!(raw instanceof Map<?, ?> entries)) {
            return false;
        }
        if (entries.isEmpty()) {
            view.remove(legacy);
            return true;
        }
        for (Object key : new ArrayList<>(entries.keySet())) {
            String nodeId = String.valueOf(key);
            if (view.value(current, nodeId) == null) {
                view.set(entries.get(key), current, nodeId);
            }
        }
        view.remove(legacy);
        return true;
    }

    private static boolean migrateBossPoints(YamlTextDocument view) {
        String legacy = FlowLoader.legacyBossSection();
        if (!(view.value(legacy) instanceof Map<?, ?> entries)) {
            return false;
        }
        boolean changed = false;
        int kept = 0;
        List<String> migratedIds = new ArrayList<>();
        for (Object key : new ArrayList<>(entries.keySet())) {
            String nodeId = String.valueOf(key);
            if (!(view.value(legacy, nodeId) instanceof Map<?, ?> node)) {
                kept++;
                continue;
            }
            Object bossId = node.get("boss_id");
            if (bossId == null || String.valueOf(bossId).isBlank()) {
                kept++;
                continue;
            }
            if (view.value("spawn_points", nodeId) != null) {
                kept++;
                continue;
            }
            view.set(bossNode(nodeId, node), "spawn_points", nodeId);
            migratedIds.add(nodeId);
            changed = true;
        }
        if (kept == 0) {
            view.remove(legacy);
        } else {
            for (String nodeId : migratedIds) {
                view.remove(legacy, nodeId);
            }
        }
        return changed;
    }

    private static Map<String, Object> bossNode(String nodeId, Map<?, ?> legacy) {
        Map<String, Object> node = new LinkedHashMap<>();
        for (Map.Entry<?, ?> field : legacy.entrySet()) {
            String name = String.valueOf(field.getKey());
            if ("boss_id".equals(name) || "source".equals(name) || "waves".equals(name)) {
                continue;
            }
            node.put(name, field.getValue());
        }
        node.put("spawn_id", nodeId);
        Object source = legacy.get("source");
        Map<String, Object> mob = new LinkedHashMap<>();
        mob.put("id", String.valueOf(legacy.get("boss_id")));
        mob.put("count", 1);
        mob.put("weight", 1);
        mob.put("source", source == null ? "vanilla" : FlowLoader.canonicalBossSource(String.valueOf(source)));
        mob.put("boss", true);
        Map<String, Object> wave = new LinkedHashMap<>();
        wave.put("interval", 1);
        wave.put("drop_loot", true);
        wave.put("mobs", new ArrayList<>(List.of(mob)));
        node.put("waves", new ArrayList<>(List.of(wave)));
        return node;
    }

    private static boolean migrateTipTypes(YamlTextDocument view) {
        if (!(view.value("tip_points") instanceof Map<?, ?> entries)) {
            return false;
        }
        boolean changed = false;
        for (Object key : new ArrayList<>(entries.keySet())) {
            String nodeId = String.valueOf(key);
            if (!(view.value("tip_points", nodeId) instanceof Map<?, ?> node)) {
                continue;
            }
            Object channel = node.get("channel");
            Object type = node.get("type");
            if (channel != null && type == null) {
                view.set(mapTipType(String.valueOf(channel)), "tip_points", nodeId, "type");
                changed = true;
            }
            if (channel != null) {
                view.remove("tip_points", nodeId, "channel");
                changed = true;
            }
            if (type != null && !mapTipType(String.valueOf(type)).equals(String.valueOf(type))) {
                view.set(mapTipType(String.valueOf(type)), "tip_points", nodeId, "type");
                changed = true;
            }
        }
        return changed;
    }

    private static String mapTipType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "message", "bossbar", "actionbar", "title" -> value;
            default -> "message";
        };
    }
}
