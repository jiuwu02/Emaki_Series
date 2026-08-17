package emaki.jiuwu.craft.skills.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.trigger.TriggerCategory;
import emaki.jiuwu.craft.corelib.trigger.TriggerDefinition;

public final class SkillTriggerDefaults {

    private SkillTriggerDefaults() {
    }

    public static List<TriggerDefinition> defaultDefinitions() {
        List<TriggerDefinition> defs = new ArrayList<>();

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

    public static List<TriggerDefinition> defaultPassiveDefinitions() {
        List<TriggerDefinition> defs = new ArrayList<>();

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

    private static TriggerDefinition simple(String id, String displayName) {
        return new TriggerDefinition(id, displayName, null, true, Set.of(), null);
    }

    private static TriggerDefinition passive(String id, String displayName) {
        return new TriggerDefinition(id, displayName, null, true, Set.of(), null, TriggerCategory.PASSIVE);
    }
}
