function register(attribute) {
  attribute.registerAttribute({
    id: "js_fire_mastery",
    displayName: "火焰精通",
    valueKind: "FLAT",
    targetType: "GENERIC",
    defaultValue: 0,
    minValue: 0,
    allowNegative: false,
    priority: 100,
    lorePatterns: ["火焰精通: \\+(?<value>[0-9.]+)"],
    description: "由捆绑JavaScript扩展示例注册的运行时属性",
    attributePower: 1.0
  });

  attribute.registerProvider({
    id: "js_fire_mastery_provider",
    priority: 100,
    function: "collectFireMastery"
  });

  attribute.onDamage({
    id: "js_fire_mastery_damage",
    priority: 100,
    damageTypes: ["fire", "magic", "lightning"],
    function: "boostFireDamage"
  });
}

// 安全默认设置：不自动授予属性。
// 如果您想根据实体状态添加仅限 JS 的属性，请编辑此函数。
function collectFireMastery(entity) {
  return [];
}

function boostFireDamage(event) {
  // 伤害钩子可以读取技能或脚本提供的显式上下文变量。
  // 示例: emaki.attribute.applyDamage(attacker, target, "fire", 10, { js_fire_mastery: 15 })
  const context = event.context();
  const mastery = Number(read(context, "js_fire_mastery", 0));
  if (!Number.isFinite(mastery) || mastery <= 0) {
    return { skipped: true, message: "未找到js_fire_mastery上下文值" };
  }

  const multiplier = 1 + mastery / 100;
  event.multiplyDamage(multiplier);
  event.setMeta("js_fire_mastery_boost", mastery);

  return {
    success: true,
    message: "JavaScript火焰精通伤害钩子已应用",
    output: {
      mastery: mastery,
      multiplier: multiplier,
      final_damage: event.finalDamage()
    }
  };
}

function read(object, key, fallback) {
  if (object == null) {
    return fallback;
  }
  if (typeof object.get === "function") {
    const value = object.get(key);
    return value == null ? fallback : value;
  }
  const value = object[key];
  return value == null ? fallback : value;
}
