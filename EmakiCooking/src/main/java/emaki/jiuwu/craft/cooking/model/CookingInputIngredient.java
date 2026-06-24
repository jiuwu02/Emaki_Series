package emaki.jiuwu.craft.cooking.model;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 统一的烹饪输入材料模型，供各工位把自身形态各异的原料（单输入 / 材料列表 / 槽位映射）
 * 归一化后传入交付链，并暴露给 JavaScript 结果规则读取。
 *
 * @param source 输入材料的物品来源 shorthand，可为空字符串
 * @param amount 本次烹饪实际消耗的数量，至少为 1
 */
public record CookingInputIngredient(String source, int amount) {

    public CookingInputIngredient {
        source = Texts.toStringSafe(source);
        amount = Math.max(1, amount);
    }

    /** {@return 供 JS 上下文使用的只读映射 {@code {source, amount}}} */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", source);
        map.put("amount", amount);
        return map;
    }
}
