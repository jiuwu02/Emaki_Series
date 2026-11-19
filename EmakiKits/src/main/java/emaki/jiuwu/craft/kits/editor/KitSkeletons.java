package emaki.jiuwu.craft.kits.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KitSkeletons {

    private KitSkeletons() {
    }

    public static Map<String, Object> skeleton(String id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("type", "generic");
        root.put("enabled", true);
        root.put("display_name", id);
        root.put("permission", "emakikits.claim");
        Map<String, Object> rewards = new LinkedHashMap<>();
        rewards.put("weighted", false);
        List<Object> fixed = new ArrayList<>();
        fixed.add(defaultRewardEntry(0));
        rewards.put("fixed", fixed);
        rewards.put("random", new ArrayList<>());
        root.put("rewards", rewards);
        return root;
    }

    public static Map<String, Object> defaultRewardEntry(int index) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "item");
        entry.put("id", "entry_" + index);
        entry.put("source", "minecraft-bread");
        entry.put("amount", 1);
        return entry;
    }

    public static Map<String, Object> defaultOptionEntry(String key) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("display", key);
        List<Object> rewards = new ArrayList<>();
        rewards.add(defaultRewardEntry(0));
        entry.put("rewards", rewards);
        return entry;
    }

    public static Map<String, Object> defaultMilestoneEntry() {
        Map<String, Object> entry = new LinkedHashMap<>();
        List<Object> rewards = new ArrayList<>();
        rewards.add(defaultRewardEntry(0));
        entry.put("rewards", rewards);
        return entry;
    }

}
