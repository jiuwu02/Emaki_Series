// ============================================================================
// EmakiItem 脚本扩展示例：自定义物品效果类型（items 绑定）。
//
// 这是演示脚本：注册一个 id 为 script_example_glow 的物品效果类型，
// 物品定义中这样使用：
//   effects:
//     - type: script_example_glow
//       level: 2
//
// parse / clear / apply 的完整契约请参考 EmakiItemApi 的 ItemEffectType /
// ItemEffect 接口文档，或 docs 站 EmakiItem 模块的 scripts 页面。
//
// 安全声明：脚本环境默认全开放（可访问任意 Java 类与服务器 API），
// 具备与服务器同级权限，请只加载你自己审查过的脚本。
//
// 释放与覆盖：此文件仅在本文件不存在时首次释放，已存在的文件不会被覆盖。
// ============================================================================

// 演示效果写入 Lore 的行前缀，clear 按它识别并移除旧行。
var LINE_PREFIX = "§7灵光 ";

items.registerEffectType({
    // 效果类型 id（必填）：会被规范化为小写 + 空格转下划线；
    // 不得与内置效果类型（variables / ea_attribute / es_skill / accessory_slot /
    // name_action / lore_action）重名。
    id: "script_example_glow",

    // parse(必填)：物品每次构建时调用，把原始效果条目转换为 effect 数据。
    // ctx.definitionId：声明该效果的物品定义 id。
    // ctx.config：effects 条目的原始配置（含 type 字段），可按属性访问。
    // 返回 null / undefined 表示跳过该条目（本例：level 非法时跳过）。
    // 返回值会作为 effect 传给下面的 clear / apply。
    parse: function (ctx) {
        var level = 1;
        if (ctx.config && ctx.config.level != null) {
            level = Number(ctx.config.level);
        }
        if (isNaN(level) || level <= 0) {
            return null;
        }
        return { level: Math.min(Math.floor(level), 10) };
    },

    // clear(可选)：在 apply 之前调用，移除上次构建写入的数据。
    // itemCtx.itemStack：正在构建的物品（Bukkit ItemStack，可直接调用其方法）。
    // 本示例把效果写入物品 Lore，所以按前缀行移除。
    clear: function (effect, itemCtx) {
        var meta = itemCtx.itemStack.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        var lore = meta.getLore();
        var kept = [];
        for (var i = 0; i < lore.size(); i++) {
            var line = String(lore.get(i));
            if (line.indexOf(LINE_PREFIX) === 0) {
                continue;
            }
            kept.push(line);
        }
        meta.setLore(kept);
        itemCtx.itemStack.setItemMeta(meta);
    },

    // apply(必填)：把效果写入正在构建的物品。
    // itemCtx.variables：定义解析后的表达式变量；itemCtx.equipSlot：槽位。
    // 本示例按 level 在 Lore 末尾追加一行演示文本（仅演示，真实用法参考契约文档）。
    apply: function (effect, itemCtx) {
        var meta = itemCtx.itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        var lore = meta.hasLore() ? meta.getLore() : [];
        lore.add(LINE_PREFIX + new Array(effect.level + 1).join("★"));
        meta.setLore(lore);
        itemCtx.itemStack.setItemMeta(meta);
    }
});
