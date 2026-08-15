package emaki.jiuwu.craft.skills.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class TriggerRegistry {

    private static final Logger LOGGER = Logger.getLogger(TriggerRegistry.class.getName());

    private final Map<String, SkillTriggerDefinition> definitions = new LinkedHashMap<>();

    public void register(SkillTriggerDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    public SkillTriggerDefinition get(String id) {
        return definitions.get(id);
    }

    public Map<String, SkillTriggerDefinition> all() {
        return Collections.unmodifiableMap(definitions);
    }

    public boolean isEnabled(String id) {
        SkillTriggerDefinition def = definitions.get(id);
        return def != null && def.enabled();
    }

    public String getDisplayName(String id) {
        SkillTriggerDefinition def = definitions.get(id);
        if (def != null) {
            return def.displayName();
        }
        LOGGER.warning("Trigger '" + id + "' is not registered; falling back to [" + id + "]");
        return "[" + id + "]";
    }

    public void clear() {
        definitions.clear();
    }

    public void loadFromConfig(Map<String, SkillTriggerDefinition> configEntries) {
        for (var entry : configEntries.entrySet()) {
            definitions.put(entry.getKey(), entry.getValue());
        }
    }

    public static List<SkillTriggerDefinition> defaultDefinitions() {
        List<SkillTriggerDefinition> defs = new ArrayList<>();

        defs.add(simple("left_click", "[左键]"));
        defs.add(simple("right_click", "[右键]"));
        defs.add(simple("shift_left_click", "[Shift + 左键]"));
        defs.add(simple("shift_right_click", "[Shift + 右键]"));
        defs.add(simple("drop_q", "[Q 键]"));

        for (int i = 1; i <= 9; i++) {
            defs.add(simple("hotbar_" + i, "[数字键 " + i + "]"));
        }

        return Collections.unmodifiableList(defs);
    }

    public static List<SkillTriggerDefinition> defaultPassiveDefinitions() {
        List<SkillTriggerDefinition> defs = new ArrayList<>();

        defs.add(passive("attack", "[攻击命中]"));
        defs.add(passive("damaged", "[受到伤害]"));
        defs.add(passive("damaged_by_entity", "[被实体伤害]"));
        defs.add(passive("death", "[死亡]"));
        defs.add(passive("kill_entity", "[击杀实体]"));
        defs.add(passive("kill_player", "[击杀玩家]"));
        defs.add(passive("shoot_bow", "[射出弓箭]"));
        defs.add(passive("arrow_hit", "[箭矢命中实体]"));
        defs.add(passive("arrow_land", "[箭矢落地]"));
        defs.add(passive("shoot_trident", "[掷出三叉戟]"));
        defs.add(passive("trident_hit", "[三叉戟命中实体]"));
        defs.add(passive("trident_land", "[三叉戟落地]"));
        defs.add(passive("break_block", "[破坏方块]"));
        defs.add(passive("place_block", "[放置方块]"));
        defs.add(passive("drop_item", "[丢弃物品]"));
        defs.add(passive("shift_drop_item", "[Shift + 丢弃物品]"));
        defs.add(passive("swap_items", "[交换主副手]"));
        defs.add(passive("shift_swap_items", "[Shift + 交换主副手]"));
        defs.add(passive("login", "[登录]"));
        defs.add(passive("sneak", "[潜行]"));
        defs.add(passive("teleport", "[传送]"));
        defs.add(passive("timer", "[定时]"));
        defs.add(passive("combo_attack", "[连击]"));

        return Collections.unmodifiableList(defs);
    }

    private static SkillTriggerDefinition simple(String id, String displayName) {
        return new SkillTriggerDefinition(id, displayName, null, true, Set.of(), null);
    }

    private static SkillTriggerDefinition passive(String id, String displayName) {
        return new SkillTriggerDefinition(id, displayName, null, true, Set.of(), null, TriggerCategory.PASSIVE);
    }
}
